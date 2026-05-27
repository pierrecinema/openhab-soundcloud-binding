package org.openhab.binding.soundcloud.internal.handler;

import static org.openhab.binding.soundcloud.internal.SoundCloudBindingConstants.*;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.soundcloud.internal.SoundCloudCallbackServlet;
import org.openhab.binding.soundcloud.internal.api.SoundCloudApiClient;
import org.openhab.binding.soundcloud.internal.api.SoundCloudOAuthClient;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudPlaylist;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTokenResponse;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrack;
import org.openhab.binding.soundcloud.internal.config.SoundCloudConfiguration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.storage.Storage;
import org.openhab.core.storage.StorageService;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.osgi.service.http.HttpService;
import org.osgi.service.http.NamespaceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@NonNullByDefault
public class SoundCloudHandler extends BaseThingHandler {

    private static final String CALLBACK_PATH      = "/soundcloud/callback";
    private static final String STORAGE_ACCESS     = "access_token";
    private static final String STORAGE_REFRESH    = "refresh_token";
    private static final String STORAGE_EXPIRES_AT = "expires_at"; // Unix-Timestamp (Sekunden)

    private final Logger logger = LoggerFactory.getLogger(SoundCloudHandler.class);
    private final SoundCloudOAuthClient oauthClient = new SoundCloudOAuthClient();
    private final Storage<String> storage;
    private final HttpService httpService;

    private @Nullable SoundCloudApiClient apiClient;
    private @Nullable SoundCloudTrack currentTrack;
    private @Nullable String currentStreamUrl;
    private @Nullable ScheduledFuture<?> tokenRefreshJob;
    private boolean servletRegistered = false;
    private String playbackState = "STOPPED";
    private final AtomicInteger searchGeneration = new AtomicInteger(0);
    private int refreshRetryCount = 0;
    private static final int MAX_REFRESH_RETRIES = 3;

    public SoundCloudHandler(Thing thing, StorageService storageService, HttpService httpService) {
        super(thing);
        this.storage = storageService.getStorage(thing.getUID().toString());
        this.httpService = httpService;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize() {
        SoundCloudConfiguration config = getConfigAs(SoundCloudConfiguration.class);

        if (config.clientId.isBlank() || config.clientSecret.isBlank() || config.redirectUri.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Client ID, Client Secret und Redirect URI sind erforderlich");
            return;
        }

        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(() -> initOAuth(config));
    }

    private void initOAuth(SoundCloudConfiguration config) {
        String storedAccess  = storage.get(STORAGE_ACCESS);
        String storedRefresh = storage.get(STORAGE_REFRESH);

        if (storedAccess != null && !storedAccess.isBlank()) {
            logger.debug("Gespeicherten OAuth-Token verwenden");
            startWithToken(config, storedAccess, storedRefresh);
            return;
        }

        // No tokens — register servlet and show auth URL
        registerServlet(config);
    }

    private void registerServlet(SoundCloudConfiguration config) {
        if (servletRegistered) return;
        try {
            httpService.registerServlet(CALLBACK_PATH,
                    new SoundCloudCallbackServlet(code -> onCodeReceived(config, code)),
                    null, null);
            servletRegistered = true;
            String authUrl = oauthClient.buildAuthorizationUrl(config.clientId, config.redirectUri);
            logger.info("SoundCloud Autorisierung erforderlich — öffne im Browser: {}", authUrl);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Nicht autorisiert. Öffne im Browser: " + authUrl);
        } catch (NamespaceException e) {
            // Servlet already registered (e.g. from a previous init) — just show URL
            servletRegistered = true;
            String authUrl = oauthClient.buildAuthorizationUrl(config.clientId, config.redirectUri);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Nicht autorisiert. Öffne im Browser: " + authUrl);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Servlet-Registrierung fehlgeschlagen: " + e.getMessage());
        }
    }

    private void unregisterServlet() {
        if (servletRegistered) {
            try {
                httpService.unregister(CALLBACK_PATH);
            } catch (Exception e) {
                logger.debug("Servlet-Deregistrierung: {}", e.getMessage());
            }
            servletRegistered = false;
        }
    }

    private void onCodeReceived(SoundCloudConfiguration config, String code) {
        unregisterServlet();
        try {
            SoundCloudTokenResponse tokens = oauthClient.exchangeCode(
                    config.clientId, config.clientSecret, config.redirectUri, code);
            saveTokens(tokens);
            apiClient = new SoundCloudApiClient(config.clientId, config.webClientId, tokens.accessToken);
            updateStatus(ThingStatus.ONLINE);
            scheduleTokenRefresh(config, tokens.expiresIn);
            logger.info("SoundCloud erfolgreich autorisiert");
        } catch (Exception e) {
            logger.warn("Token-Austausch fehlgeschlagen: {}", e.getMessage());
            // registerServlet setzt den Status bereits mit der Auth-URL;
            // kein zweites updateStatus, das die URL überschreiben würde.
            registerServlet(config);
        }
    }

    private void startWithToken(SoundCloudConfiguration config, String accessToken,
            @Nullable String refreshToken) {
        SoundCloudApiClient client = new SoundCloudApiClient(config.clientId, config.webClientId, accessToken);
        apiClient = client;
        try {
            client.searchTracks("test");
            updateStatus(ThingStatus.ONLINE);
            if (refreshToken != null && !refreshToken.isBlank()) {
                scheduleTokenRefresh(config, remainingTokenSeconds());
            }
        } catch (Exception e) {
            if (refreshToken != null && !refreshToken.isBlank()) {
                logger.debug("Token abgelehnt — versuche Refresh");
                refreshAccessToken(config, refreshToken);
            } else {
                storage.remove(STORAGE_ACCESS);
                storage.remove(STORAGE_REFRESH);
                registerServlet(config);
            }
        }
    }

    /** Speichert Access-Token, Refresh-Token und berechneten Ablaufzeitpunkt. */
    private void saveTokens(SoundCloudTokenResponse tokens) {
        storage.put(STORAGE_ACCESS,  tokens.accessToken);
        storage.put(STORAGE_REFRESH, tokens.refreshToken);
        long expiresAt = System.currentTimeMillis() / 1000L + tokens.expiresIn;
        storage.put(STORAGE_EXPIRES_AT, String.valueOf(expiresAt));
        logger.debug("Tokens gespeichert, läuft ab in {}s (um {})", tokens.expiresIn, expiresAt);
    }

    /**
     * Berechnet die verbleibende Gültigkeit des gespeicherten Access-Tokens.
     * Gibt mindestens 60 Sekunden zurück (sofortiger Refresh wenn Token fast/schon abgelaufen).
     */
    private long remainingTokenSeconds() {
        String stored = storage.get(STORAGE_EXPIRES_AT);
        if (stored == null) return 3540; // kein Zeitstempel → konservativ 59 min
        try {
            long expiresAt = Long.parseLong(stored);
            long remaining = expiresAt - System.currentTimeMillis() / 1000L;
            if (remaining <= 120) {
                logger.info("Token läuft in {}s ab — sofortiger Refresh", remaining);
                return 60; // fast abgelaufen → sofort refreshen
            }
            logger.debug("Token läuft in {}s ab — Refresh in {}s geplant", remaining, remaining - 60);
            return remaining - 60; // 60s Puffer vor Ablauf
        } catch (NumberFormatException e) {
            return 3540;
        }
    }

    private void scheduleTokenRefresh(SoundCloudConfiguration config, long expiresIn) {
        ScheduledFuture<?> existing = tokenRefreshJob;
        if (existing != null) existing.cancel(false);
        long delay = Math.max(expiresIn - 60, 60);
        tokenRefreshJob = scheduler.schedule(() -> {
            String stored = storage.get(STORAGE_REFRESH);
            if (stored != null) refreshAccessToken(config, stored);
        }, delay, TimeUnit.SECONDS);
    }

    private void refreshAccessToken(SoundCloudConfiguration config, String refreshToken) {
        try {
            SoundCloudTokenResponse tokens = oauthClient.refreshToken(
                    config.clientId, config.clientSecret, refreshToken);
            saveTokens(tokens);
            refreshRetryCount = 0;
            SoundCloudApiClient client = apiClient;
            if (client != null) {
                client.setOauthToken(tokens.accessToken);
            } else {
                apiClient = new SoundCloudApiClient(config.clientId, config.webClientId, tokens.accessToken);
            }
            updateStatus(ThingStatus.ONLINE);
            scheduleTokenRefresh(config, tokens.expiresIn);
            logger.info("OAuth-Token erfolgreich erneuert");
        } catch (Exception e) {
            boolean isTokenInvalid = e.getMessage() != null && e.getMessage().contains("HTTP 400");

            if (!isTokenInvalid && refreshRetryCount < MAX_REFRESH_RETRIES) {
                // Netzwerkfehler oder temporärer SoundCloud-Fehler → retry mit Backoff
                refreshRetryCount++;
                long retryDelay = refreshRetryCount == 1 ? 60 : refreshRetryCount == 2 ? 300 : 900;
                logger.warn("Token-Refresh fehlgeschlagen (Versuch {}/{}): {} — retry in {}s",
                        refreshRetryCount, MAX_REFRESH_RETRIES, e.getMessage(), retryDelay);
                scheduleTokenRefresh(config, retryDelay);
                return;
            }

            logger.warn("Token-Refresh endgültig fehlgeschlagen: {} — prüfe ob Access-Token noch gültig",
                    e.getMessage());
            refreshRetryCount = 0;

            // Erst prüfen ob der bestehende Access-Token noch funktioniert,
            // bevor Tokens gelöscht und Re-Auth verlangt wird.
            SoundCloudApiClient client = apiClient;
            if (client != null) {
                try {
                    client.searchTracks("test");
                    logger.info("Access-Token noch gültig — bleibe online, nächster Refresh-Versuch in 5 Minuten");
                    updateStatus(ThingStatus.ONLINE);
                    scheduleTokenRefresh(config, 300);
                    return;
                } catch (Exception e2) {
                    logger.debug("Access-Token ebenfalls abgelaufen: {}", e2.getMessage());
                }
            }
            storage.remove(STORAGE_ACCESS);
            storage.remove(STORAGE_REFRESH);
            storage.remove(STORAGE_EXPIRES_AT);
            registerServlet(config);
        }
    }

    @Override
    public void dispose() {
        unregisterServlet();
        ScheduledFuture<?> job = tokenRefreshJob;
        if (job != null) {
            job.cancel(true);
            tokenRefreshJob = null;
        }
        apiClient = null;
    }

    // -------------------------------------------------------------------------
    // Command handling
    // -------------------------------------------------------------------------

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            refreshChannels();
            return;
        }
        switch (channelUID.getId()) {
            case CHANNEL_SEARCH_QUERY:
                search(command.toString());
                break;
            case CHANNEL_PLAYBACK_STATE:
                handlePlaybackCommand(command.toString().toUpperCase());
                break;
            case CHANNEL_TRACK_ID:
                loadTrack(parseLong(command.toString()));
                break;
            case CHANNEL_PLAYLIST_ID:
                loadPlaylist(parseLong(command.toString()));
                break;
            case CHANNEL_CHROMECAST_TARGET:
                // Store the selected Chromecast target item name
                updateState(CHANNEL_CHROMECAST_TARGET, new StringType(command.toString()));
                break;
            default:
                logger.debug("Kein Handler für Channel {}", channelUID.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Player actions
    // -------------------------------------------------------------------------

    public void search(String query) {
        SoundCloudApiClient client = apiClient;
        if (client == null) return;
        // Neue Generation: überholt eine eventuell noch laufende ältere Suche
        int gen = searchGeneration.incrementAndGet();
        // Alte Ergebnisse sofort leeren, damit nie veraltete Results stehen bleiben
        updateState(CHANNEL_SEARCH_RESULTS, new StringType("[]"));
        scheduler.submit(() -> {
            try {
                List<SoundCloudTrack> tracks = client.searchTracks(query);
                // Verwerfen falls zwischenzeitlich eine neuere Suche gestartet wurde
                if (gen != searchGeneration.get()) {
                    logger.debug("Suche '{}' überholt durch neuere Anfrage — verworfen", query);
                    return;
                }
                JsonArray arr = new JsonArray();
                for (SoundCloudTrack t : tracks) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", t.id);
                    o.addProperty("title", t.title != null ? t.title : "");
                    o.addProperty("artist", t.user != null && t.user.username != null ? t.user.username : "");
                    o.addProperty("artwork", hqArtwork(t.artworkUrl));
                    o.addProperty("duration", t.duration / 1000);
                    arr.add(o);
                }
                updateState(CHANNEL_SEARCH_RESULTS, new StringType(arr.toString()));
            } catch (Exception e) {
                logger.warn("Suche '{}' fehlgeschlagen: {}", query, e.getMessage());
                // Ergebnisse wurden bereits geleert — kein Rückfall auf alte Results
            }
        });
    }

    public void loadTrack(long trackId) {
        if (trackId <= 0) return;
        SoundCloudApiClient client = apiClient;
        if (client == null) return;
        scheduler.submit(() -> {
            try {
                SoundCloudTrack track = client.getTrackV2(trackId);
                String streamUrl = client.resolveStreamUrlV2(track);
                if (streamUrl == null) {
                    logger.warn("Kein Stream-URL für Track {} ({})", trackId, track.title);
                    return;
                }
                currentTrack = track;
                currentStreamUrl = streamUrl;
                playbackState = "PLAYING";
                applyTrackToChannels(track, streamUrl);
            } catch (Exception e) {
                logger.warn("Track {} konnte nicht geladen werden: {}", trackId, e.getMessage());
            }
        });
    }

    public void loadPlaylist(long playlistId) {
        if (playlistId <= 0) return;
        SoundCloudApiClient client = apiClient;
        if (client == null) return;
        scheduler.submit(() -> {
            try {
                SoundCloudPlaylist playlist = client.getPlaylist(playlistId);
                if (!playlist.tracks.isEmpty()) loadTrack(playlist.tracks.get(0).id);
            } catch (Exception e) {
                logger.warn("Playlist {} konnte nicht geladen werden: {}", playlistId, e.getMessage());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void handlePlaybackCommand(String cmd) {
        switch (cmd) {
            case "PLAY":
                if (currentTrack != null) {
                    playbackState = "PLAYING";
                    updateState(CHANNEL_PLAYBACK_STATE, new StringType("PLAYING"));
                }
                break;
            case "PAUSE":
                playbackState = "PAUSED";
                updateState(CHANNEL_PLAYBACK_STATE, new StringType("PAUSED"));
                break;
            case "STOP":
                playbackState = "STOPPED";
                currentTrack = null;
                currentStreamUrl = null;
                updateState(CHANNEL_PLAYBACK_STATE, new StringType("STOPPED"));
                updateState(CHANNEL_STREAM_URL, new StringType(""));
                updateState(CHANNEL_TITLE, new StringType(""));
                updateState(CHANNEL_ARTIST, new StringType(""));
                break;
        }
    }

    private void applyTrackToChannels(SoundCloudTrack track, String streamUrl) {
        updateState(CHANNEL_TRACK_ID, new StringType(String.valueOf(track.id)));
        updateState(CHANNEL_TITLE, new StringType(track.title != null ? track.title : ""));
        updateState(CHANNEL_ARTIST, new StringType(track.user != null && track.user.username != null ? track.user.username : ""));
        updateState(CHANNEL_ARTWORK_URL, new StringType(hqArtwork(track.artworkUrl)));
        updateState(CHANNEL_DURATION, new DecimalType(track.duration / 1000));
        updateState(CHANNEL_STREAM_URL, new StringType(streamUrl));
        updateState(CHANNEL_PLAYBACK_STATE, new StringType("PLAYING"));
    }

    private void refreshChannels() {
        SoundCloudTrack track = currentTrack;
        String streamUrl = currentStreamUrl;
        if (track != null && streamUrl != null) applyTrackToChannels(track, streamUrl);
        updateState(CHANNEL_PLAYBACK_STATE, new StringType(playbackState));
    }

    private static String hqArtwork(@Nullable String url) {
        return (url == null || url.isEmpty()) ? "" : url.replace("-large.", "-t500x500.");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
