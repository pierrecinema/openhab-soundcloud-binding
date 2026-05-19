package org.openhab.binding.soundcloud.internal.api;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.soundcloud.internal.api.dto.SoundCloudTokenResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

@NonNullByDefault
public class SoundCloudOAuthClient {

    private static final String AUTH_URL   = "https://secure.soundcloud.com/authorize";
    private static final String TOKEN_URL  = "https://secure.soundcloud.com/oauth/token";

    private final Logger logger = LoggerFactory.getLogger(SoundCloudOAuthClient.class);
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Returns the URL the user must open in a browser to authorize the app. */
    public String buildAuthorizationUrl(String clientId, String redirectUri) {
        return AUTH_URL
                + "?client_id="    + encode(clientId)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code";
    }

    /** Exchanges a one-time authorization code for access + refresh tokens. */
    public SoundCloudTokenResponse exchangeCode(String clientId, String clientSecret,
            String redirectUri, String code) throws IOException, InterruptedException {
        String body = "grant_type=authorization_code"
                + "&client_id="     + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&redirect_uri="  + encode(redirectUri)
                + "&code="          + encode(code);
        logger.debug("Exchanging authorization code for tokens");
        return postToken(body);
    }

    /**
     * Uses a refresh token to obtain a new access token.
     * Note: redirect_uri is intentionally omitted — RFC 6749 makes it optional
     * for refresh grants and SoundCloud rejects the request if it is included.
     */
    public SoundCloudTokenResponse refreshToken(String clientId, String clientSecret,
            String refreshToken) throws IOException, InterruptedException {
        String body = "grant_type=refresh_token"
                + "&client_id="     + encode(clientId)
                + "&client_secret=" + encode(clientSecret)
                + "&refresh_token=" + encode(refreshToken);
        logger.debug("Refreshing OAuth access token");
        return postToken(body);
    }

    private SoundCloudTokenResponse postToken(String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Token request failed: HTTP "
                    + response.statusCode() + " — " + response.body());
        }
        return gson.fromJson(response.body(), SoundCloudTokenResponse.class);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
