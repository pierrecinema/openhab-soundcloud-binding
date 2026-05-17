package org.openhab.binding.soundcloud.internal.handler;

import static org.openhab.binding.soundcloud.internal.SoundCloudBindingConstants.*;

import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.soundcloud.internal.api.SoundCloudApiClient;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudPlaylist;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrack;
import org.openhab.binding.soundcloud.internal.config.SoundCloudConfiguration;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.StringType;
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

    private final Logger logger = LoggerFactory.getLogger(SoundCloudHandler.class);

    private @Nullable SoundCloudApiClient apiClient;
    private @Nullable SoundCloudTrack currentTrack;
    private @Nullable String currentStreamUrl;
    private String playbackState = "STOPPED";

    public SoundCloudHandler(Thing thing) {
        super(thing);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void initialize() {
        SoundCloudConfiguration config = getConfigAs(SoundCloudConfiguration.class);
        if (config.clientId.isBlank()) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Client ID must not be empty");
            return;
        }
        apiClient = new SoundCloudApiClient(config.clientId, config.oauthToken);
        updateStatus(ThingStatus.UNKNOWN);
        scheduler.execute(this::validateConnection);
    }

    private void validateConnection() {
        SoundCloudApiClient client = apiClient;
        if (client == null) {
            return;
        }
        try {
            client.searchTracks("test");
            updateStatus(ThingStatus.ONLINE);
        } catch (Exception e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Cannot reach SoundCloud API: " + e.getMessage());
        }
    }

    @Override
    public void dispose() {
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
                logger.debug("No handler for channel {}", channelUID.getId());
        }
    }

    // -------------------------------------------------------------------------
    // Public actions (callable from rules)
    // -------------------------------------------------------------------------

    public void search(String query) {
        SoundCloudApiClient client = apiClient;
        if (client == null) {
            return;
        }
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
                logger.debug("Search '{}' → {} results", query, tracks.size());
            } catch (Exception e) {
                logger.warn("Search failed: {}", e.getMessage());
            }
        });
    }

    public void loadTrack(long trackId) {
        if (trackId <= 0) {
            return;
        }
        SoundCloudApiClient client = apiClient;
        if (client == null) {
            return;
        }
        scheduler.submit(() -> {
            try {
                SoundCloudTrack track = client.getTrack(trackId);
                String streamUrl = client.getStreamUrl(track);
                if (streamUrl == null) {
                    logger.warn("No stream URL for track {}", trackId);
                    return;
                }
                currentTrack = track;
                currentStreamUrl = streamUrl;
                playbackState = "PLAYING";
                applyTrackToChannels(track, streamUrl);
            } catch (Exception e) {
                logger.warn("Failed to load track {}: {}", trackId, e.getMessage());
            }
        });
    }

    public void loadPlaylist(long playlistId) {
        if (playlistId <= 0) {
            return;
        }
        SoundCloudApiClient client = apiClient;
        if (client == null) {
            return;
        }
        scheduler.submit(() -> {
            try {
                SoundCloudPlaylist playlist = client.getPlaylist(playlistId);
                if (!playlist.tracks.isEmpty()) {
                    loadTrack(playlist.tracks.get(0).id);
                }
            } catch (Exception e) {
                logger.warn("Failed to load playlist {}: {}", playlistId, e.getMessage());
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

    /** Upgrades SoundCloud thumbnail URLs from 100×100 to 500×500. */
    private static String hqArtwork(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
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
