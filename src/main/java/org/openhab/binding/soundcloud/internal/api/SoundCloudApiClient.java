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
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrack;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTrackSearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@NonNullByDefault
public class SoundCloudApiClient {

    private static final String API_V1       = "https://api.soundcloud.com";
    private static final String API_V2       = "https://api-v2.soundcloud.com";
    private static final String APP_VERSION  = "1776363554";
    private static final int    SEARCH_LIMIT = 20;

    private final Logger logger = LoggerFactory.getLogger(SoundCloudApiClient.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient;
    private final HttpClient noRedirectClient;
    private final String clientId;
    private final String webClientId;
    private @Nullable String oauthToken;

    public SoundCloudApiClient(String clientId, String webClientId, @Nullable String oauthToken) {
        this.clientId    = clientId;
        this.webClientId = webClientId;
        this.oauthToken  = oauthToken;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.noRedirectClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public void setOauthToken(String token) {
        this.oauthToken = token;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public List<SoundCloudTrack> searchTracks(String query) throws IOException, InterruptedException {
        String url = API_V2 + "/search/tracks?q=" + encode(query)
                + "&limit=" + SEARCH_LIMIT + "&client_id=" + webClientId + "&app_version=" + APP_VERSION;
        logger.debug("Search (api-v2): {}", url);
        SoundCloudTrackSearchResponse resp = gson.fromJson(getNoAuth(url), SoundCloudTrackSearchResponse.class);
        return resp.collection;
    }

    public List<SoundCloudPlaylist> searchPlaylists(String query) throws IOException, InterruptedException {
        String url = API_V2 + "/search/playlists?q=" + encode(query)
                + "&limit=10&client_id=" + webClientId + "&app_version=" + APP_VERSION;
        SoundCloudPlaylistSearchResponse resp = gson.fromJson(getNoAuth(url), SoundCloudPlaylistSearchResponse.class);
        return resp.collection;
    }

    public SoundCloudTrack getTrack(long trackId) throws IOException, InterruptedException {
        return gson.fromJson(get(API_V1 + "/tracks/" + trackId), SoundCloudTrack.class);
    }

    public SoundCloudPlaylist getPlaylist(long playlistId) throws IOException, InterruptedException {
        return gson.fromJson(get(API_V1 + "/playlists/" + playlistId), SoundCloudPlaylist.class);
    }

    /**
     * Resolves the direct CDN stream URL for a track.
     * The v1 API returns a 302 redirect to the actual MP3 — we capture the Location header.
     */
    public @Nullable String getStreamUrl(SoundCloudTrack track) throws IOException, InterruptedException {
        String streamUrl = track.streamUrl;
        if (streamUrl == null || streamUrl.isEmpty()) {
            logger.warn("No stream_url for track '{}' (id={})", track.title, track.id);
            return null;
        }

        String url = streamUrl + "?client_id=" + clientId;
        logger.debug("Resolving stream URL: {}", url);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "*/*")
                .GET();

        String token = oauthToken;
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", "OAuth " + token);
        }

        HttpResponse<String> response = noRedirectClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 301 || response.statusCode() == 302) {
            return response.headers().firstValue("Location").orElse(null);
        }
        logger.warn("Unexpected status {} resolving stream for track '{}'", response.statusCode(), track.title);
        return null;
    }

    // -------------------------------------------------------------------------
    // HTTP helpers
    // -------------------------------------------------------------------------

    /** Sends request with OAuth token (for authenticated endpoints). */
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

    /** Sends request without any auth or client_id appended — URL already contains all parameters. */
    private String getNoAuth(String url) throws IOException, InterruptedException {
        logger.debug("GET (no-auth) {}", url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json; charset=utf-8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SoundCloud API returned HTTP " + response.statusCode() + " for " + url);
        }
        return response.body();
    }

    private String appendClientId(String url) {
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "client_id=" + clientId;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
