package org.openhab.binding.soundcloud.internal.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudPlaylist;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudPlaylistSearchResponse;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudStreamResponse;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrack;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrackSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@NonNullByDefault
public class SoundCloudApiClient {

    private static final String API_BASE = "https://api-v2.soundcloud.com";
    private static final int SEARCH_LIMIT = 20;

    private final Logger logger = LoggerFactory.getLogger(SoundCloudApiClient.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final String clientId;
    private @Nullable String oauthToken;

    public SoundCloudApiClient(String clientId, @Nullable String oauthToken) {
        this.clientId = clientId;
        this.oauthToken = oauthToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public void setOauthToken(String token) {
        this.oauthToken = token;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<SoundCloudTrack> searchTracks(String query) throws IOException, InterruptedException {
        String url = API_BASE + "/search/tracks?q=" + encode(query) + "&limit=" + SEARCH_LIMIT;
        SoundCloudTrackSearchResponse resp = gson.fromJson(get(url), SoundCloudTrackSearchResponse.class);
        return resp.collection;
    }

    public List<SoundCloudPlaylist> searchPlaylists(String query) throws IOException, InterruptedException {
        String url = API_BASE + "/search/playlists?q=" + encode(query) + "&limit=10";
        SoundCloudPlaylistSearchResponse resp = gson.fromJson(get(url), SoundCloudPlaylistSearchResponse.class);
        return resp.collection;
    }

    public SoundCloudTrack getTrack(long trackId) throws IOException, InterruptedException {
        String url = API_BASE + "/tracks/" + trackId;
        return gson.fromJson(get(url), SoundCloudTrack.class);
    }

    public SoundCloudPlaylist getPlaylist(long playlistId) throws IOException, InterruptedException {
        String url = API_BASE + "/playlists/" + playlistId;
        return gson.fromJson(get(url), SoundCloudPlaylist.class);
    }

    /**
     * Resolves the progressive (direct MP3) stream URL for a track.
     * Falls back to the first available transcoding if no progressive one exists.
     * Returns null when the track has no transcodings.
     */
    public @Nullable String getStreamUrl(SoundCloudTrack track) throws IOException, InterruptedException {
        String transcodingUrl = null;

        for (SoundCloudTrack.Transcoding t : track.media.transcodings) {
            if ("progressive".equals(t.format.protocol)) {
                transcodingUrl = t.url;
                break;
            }
        }
        if (transcodingUrl == null && !track.media.transcodings.isEmpty()) {
            transcodingUrl = track.media.transcodings.get(0).url;
        }
        if (transcodingUrl == null) {
            logger.warn("No transcoding available for track '{}' (id={})", track.title, track.id);
            return null;
        }

        String response = get(transcodingUrl);
        SoundCloudStreamResponse streamResp = gson.fromJson(response, SoundCloudStreamResponse.class);
        return streamResp.url.isEmpty() ? null : streamResp.url;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    private String get(String url) throws IOException, InterruptedException {
        String fullUrl = appendClientId(url);
        logger.debug("GET {}", fullUrl);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json; charset=utf-8")
                .GET();

        String token = oauthToken;
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "OAuth " + token);
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SoundCloud API returned HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private String appendClientId(String url) {
        return url + (url.contains("?") ? "&" : "?") + "client_id=" + clientId;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
