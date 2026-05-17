package org.openhab.binding.soundcloud.internal.handler;

import static org.openhab.binding.soundcloud.internal.SoundCloudBindingConstants.*;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

@NonNullByDefault
public class SoundCloudHandler extends BaseThingHandler {

    private static final String STORAGE_ACCESS_TOKEN  = "access_token";
    private static final String STORAGE_REFRESH_TOKEN = "refresh_token";

    private final Logger logger = LoggerFactory.getLogger(SoundCloudHandler.class);
    private final SoundCloudOAuthClient oauthClient = new SoundCloudOAuthClient();
    private final Storage<String> storage;

    private @Nullable SoundCloudApiClient apiClient;
    private @Nullable SoundCloudTrack currentTrack;
    private @Nullable String currentStreamUrl;
    private @Nullable ScheduledFuture<?> tokenRefreshJob;
    private String playbackState = "STOPPED";

    public SoundCloudHandler(Thing thing, StorageService storageService) {
        super(thing);
        this.storage = storageService.getStorage(thing.getUID().toString());
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
        // 1. Try stored tokens from a previous run
        String storedAccess  = storage.get(STORAGE_ACCESS_TOKEN);
        String storedRefresh = storage.get(STORAGE_REFRESH_TOKEN);

        if (storedAccess != null && !storedAccess.isBlank()) {
            logger.debug("Using stored OAuth access token");
            startWithToken(config, storedAccess, storedRefresh);
            return;
        }

        // 2. Try authorization code from config (first-time setup)
        String code = config.authorizationCode;
        if (code != null && !code.isBlank()) {
            logger.debug("Exchanging authorization code for tokens");
            try {
                SoundCloudTokenResponse tokens = oauthClient.exchangeCode(
                        config.clientId, config.clientSecret, config.redirectUri, code);
                storage.put(STORAGE_ACCESS_TOKEN,  tokens.accessToken);
                storage.put(STORAGE_REFRESH_TOKEN, tokens.refreshToken);
                startWithToken(config, tokens.accessToken, tokens.refreshToken);
                scheduleTokenRefresh(config, tokens.expiresIn);
            } catch (Exception e) {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                        "Code ungültig oder abgelaufen: " + e.getMessage());
            }
            return;
        }

        // 3. No tokens yet — show authorization URL
        String authUrl = oauthClient.buildAuthorizationUrl(config.clientId, config.redirectUri);
        logger.info("SoundCloud nicht autorisiert. Öffne diese URL im Browser: {}", authUrl);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                "Nicht autorisiert. Öffne diese URL im Browser, melde dich an, "
                + "kopiere dann den 'code' Parameter aus der Redirect-URL und trage ihn "
                + "als 'Authorization Code' in der Thing-Konfiguration ein. URL: " + authUrl);
    }

    private void startWithToken(SoundCloudConfiguration config, String accessToken,
            @Nullable String refreshToken) {
        SoundCloudApiClient client = new SoundCloudApiClient(config.clientId, accessToken);
        apiClient = client;
        try {
            client.searchTracks("test");
            updateStatus(ThingStatus.ONLINE);
            if (refreshToken != null && !refreshToken.isBlank()) {
                scheduleTokenRefresh(config, 3540); // refresh after ~59 min by default
            }
        } catch (Exception e) {
            // Token might be expired — try refresh
            if (refreshToken != null && !refreshToken.isBlank()) {
                logger.debug("Access token rejected, attempting refresh");
                refreshAccessToken(config, refreshToken);
            } else {
                storage.remove(STORAGE_ACCESS_TOKEN);
                storage.remove(STORAGE_REFRESH_TOKEN);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                        "Token abgelaufen und kein Refresh-Token vorhanden. Bitte neu autorisieren.");
            }
        }
    }

    private void scheduleTokenRefresh(SoundCloudConfiguration config, long expiresIn) {
        ScheduledFuture<?> existing = tokenRefreshJob;
        if (existing != null) {
            existing.cancel(false);
        }
        long delay = Math.max(expiresIn - 60, 60);
        tokenRefreshJob = scheduler.schedule(() -> {
            String stored = storage.get(STORAGE_REFRESH_TOKEN);
            if (stored != null) {
                refreshAccessToken(config, stored);
            }
        }, delay, TimeUnit.SECONDS);
        logger.debug("Token-Refresh geplant in {} Sekunden", delay);
    }

    private void refreshAccessToken(SoundCloudConfiguration config, String refreshToken) {
        try {
            SoundCloudTokenResponse tokens = oauthClient.refreshToken(
                    config.clientId, config.clientSecret, config.redirectUri, refreshToken);
            storage.put(STORAGE_ACCESS_TOKEN,  tokens.accessToken);
            storage.put(STORAGE_REFRESH_TOKEN, tokens.refreshToken);
            SoundCloudApiClient client = apiClient;
            if (client != null) {
                client.setOauthToken(tokens.accessToken);
            } else {
                apiClient = new SoundCloudApiClient(config.clientId, tokens.accessToken);
            }
            updateStatus(ThingStatus.ONLINE);
            scheduleTokenRefresh(config, tokens.expiresIn);
            logger.info("OAuth Token erfolgreich erneuert");
        } catch (Exception e) {
            storage.remove(STORAGE_ACCESS_TOKEN);
            storage.remove(STORAGE_REFRESH_TOKEN);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Token-Refresh fehlgeschlagen: " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
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
        scheduler.submit(() -> {
            try {
                List<SoundCloudTrack> tracks = client.searchTracks(query);
                JsonArray arr = new JsonArray();
                for (SoundCloudTrack t : tracks) {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", t.id);
                    o.addProperty("title", t.title);
                    o.addProperty("artist", t.user.username);
                    o.addProperty("artwork", hqArtwork(t.artworkUrl));
                    o.addProperty("duration", t.duration / 1000);
                    arr.add(o);
                }
                updateState(CHANNEL_SEARCH_RESULTS, new StringType(arr.toString()));
                logger.debug("Suche '{}' → {} Ergebnisse", query, tracks.size());
            } catch (Exception e) {
                logger.warn("Suche fehlgeschlagen: {}", e.getMessage());
            }
        });
    }

    public void loadTrack(long trackId) {
        if (trackId <= 0) return;
        SoundCloudApiClient client = apiClient;
        if (client == null) return;
        scheduler.submit(() -> {
            try {
                SoundCloudTrack track = client.getTrack(trackId);
                String streamUrl = client.getStreamUrl(track);
                if (streamUrl == null) {
                    logger.warn("Kein Stream-URL für Track {}", trackId);
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
                if (!playlist.tracks.isEmpty()) {
                    loadTrack(playlist.tracks.get(0).id);
                }
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
        updateState(CHANNEL_TITLE, new StringType(track.title));
        updateState(CHANNEL_ARTIST, new StringType(track.user.username));
        updateState(CHANNEL_ARTWORK_URL, new StringType(hqArtwork(track.artworkUrl)));
        updateState(CHANNEL_DURATION, new DecimalType(track.duration / 1000));
        updateState(CHANNEL_STREAM_URL, new StringType(streamUrl));
        updateState(CHANNEL_PLAYBACK_STATE, new StringType("PLAYING"));
    }

    private void refreshChannels() {
        SoundCloudTrack track = currentTrack;
        String streamUrl = currentStreamUrl;
        if (track != null && streamUrl != null) {
            applyTrackToChannels(track, streamUrl);
        }
        updateState(CHANNEL_PLAYBACK_STATE, new StringType(playbackState));
    }

    private static String hqArtwork(String url) {
        if (url.isEmpty()) return "";
        return url.replace("-large.", "-t500x500.");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
