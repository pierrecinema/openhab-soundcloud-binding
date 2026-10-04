package org.openhab.binding.soundcloud.internal;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proxies a SoundCloud HLS m3u8 playlist and skips segments up to a given time offset,
 * enabling Chromecast to start playback from a specific position.
 *
 * GET /soundcloud/seek?url=ENCODED_M3U8_URL&from=SECONDS
 */
@NonNullByDefault
public class SoundCloudSeekServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(SoundCloudSeekServlet.class);
    private final HttpClient httpClient;

    public SoundCloudSeekServlet() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String urlParam = req.getParameter("url");
        String fromParam = req.getParameter("from");

        if (urlParam == null || fromParam == null) {
            resp.sendError(400, "Missing url or from parameter");
            return;
        }

        String m3u8Url = URLDecoder.decode(urlParam, StandardCharsets.UTF_8);
        int fromSeconds;
        try {
            fromSeconds = Integer.parseInt(fromParam);
        } catch (NumberFormatException e) {
            resp.sendError(400, "Invalid from parameter");
            return;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("SoundCloud HLS returned {}", response.statusCode());
                resp.sendError(502, "SoundCloud returned " + response.statusCode());
                return;
            }

            String modified = buildSeekPlaylist(response.body(), fromSeconds);

            resp.setContentType("application/x-mpegURL");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(modified);
            logger.debug("Seek-Playlist ab {}s ausgeliefert ({} Zeichen)", fromSeconds, modified.length());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.sendError(503, "Interrupted");
        } catch (Exception e) {
            logger.warn("Seek-Proxy fehlgeschlagen: {}", e.getMessage());
            resp.sendError(500, "Proxy error: " + e.getMessage());
        }
    }

    /** Parses the m3u8 and returns a new playlist starting at the segment containing fromSeconds. */
    private String buildSeekPlaylist(String m3u8, int fromSeconds) {
        String[] lines = m3u8.split("\r?\n");

        List<String> headerLines = new ArrayList<>();
        List<String> extinfLines = new ArrayList<>();
        List<String> segmentUrls = new ArrayList<>();
        List<Double> durations = new ArrayList<>();

        String pendingExtinf = null;
        boolean segmentsStarted = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("#EXTINF:")) {
                segmentsStarted = true;
                pendingExtinf = line;
            } else if (pendingExtinf != null && !line.startsWith("#")) {
                extinfLines.add(pendingExtinf);
                segmentUrls.add(line);
                durations.add(parseDuration(pendingExtinf));
                pendingExtinf = null;
            } else if (!segmentsStarted && !line.equals("#EXT-X-ENDLIST")) {
                // Keep header lines but skip EXT-X-START (we won't add a new one)
                if (!line.startsWith("#EXT-X-START")) {
                    headerLines.add(line);
                }
            }
        }

        // Find first segment that covers fromSeconds
        double cumulative = 0.0;
        int startIdx = 0;
        for (int i = 0; i < durations.size(); i++) {
            double segEnd = cumulative + durations.get(i);
            if (segEnd > fromSeconds) {
                startIdx = i;
                break;
            }
            cumulative += durations.get(i);
            startIdx = i + 1;
        }
        if (startIdx >= segmentUrls.size()) {
            startIdx = Math.max(0, segmentUrls.size() - 1);
        }

        StringBuilder sb = new StringBuilder();
        for (String h : headerLines) sb.append(h).append("\n");
        for (int i = startIdx; i < segmentUrls.size(); i++) {
            sb.append(extinfLines.get(i)).append("\n");
            sb.append(segmentUrls.get(i)).append("\n");
        }
        sb.append("#EXT-X-ENDLIST\n");

        logger.debug("m3u8: {} Segmente gesamt, starte ab Index {} ({}s)", segmentUrls.size(), startIdx, fromSeconds);
        return sb.toString();
    }

    private double parseDuration(String extinf) {
        // "#EXTINF:10.009," or "#EXTINF:10.009"
        try {
            String content = extinf.substring(8);
            int comma = content.indexOf(',');
            String s = comma >= 0 ? content.substring(0, comma) : content;
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 10.0;
        }
    }
}
