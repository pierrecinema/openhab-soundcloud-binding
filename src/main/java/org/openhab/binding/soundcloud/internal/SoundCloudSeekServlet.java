package org.openhab.binding.soundcloud.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
 * Seek-Proxy für SoundCloud-Streams.
 *
 * MP3-Modus  (type=mp3):  Range-Request auf progressive URL, liefert audio/mpeg ab Byte-Offset.
 * HLS-Modus  (type=hls):  Parst m3u8 und überspringt Segmente bis zum Ziel-Offset.
 *
 * GET /soundcloud/seek?url=ENCODED_URL&from=SECONDS[&type=mp3|hls]
 */
@NonNullByDefault
public class SoundCloudSeekServlet extends HttpServlet {

    private static final long serialVersionUID = 2L;
    private static final int BYTES_PER_SECOND_128KBPS = 16000; // 128 kbps CBR
    private static final int STREAM_BUFFER_SIZE = 8192;

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
        String typeParam = req.getParameter("type");

        if (urlParam == null || fromParam == null) {
            resp.sendError(400, "Missing url or from parameter");
            return;
        }

        String streamUrl = URLDecoder.decode(urlParam, StandardCharsets.UTF_8);
        int fromSeconds;
        try {
            fromSeconds = Integer.parseInt(fromParam);
        } catch (NumberFormatException e) {
            resp.sendError(400, "Invalid from parameter");
            return;
        }

        if ("mp3".equals(typeParam)) {
            handleMp3Seek(streamUrl, fromSeconds, resp);
        } else {
            handleHlsSeek(streamUrl, fromSeconds, resp);
        }
    }

    // -------------------------------------------------------------------------
    // MP3 Byte-Range Seek
    // -------------------------------------------------------------------------

    private void handleMp3Seek(String progressiveUrl, int fromSeconds, HttpServletResponse resp)
            throws IOException {
        long byteOffset = (long) fromSeconds * BYTES_PER_SECOND_128KBPS;
        logger.info("MP3-Seek: {}s → Byte-Offset {}", fromSeconds, byteOffset);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(progressiveUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Range", "bytes=" + byteOffset + "-")
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            int status = response.statusCode();
            if (status != 206 && status != 200) {
                logger.warn("Progressive MP3 lieferte HTTP {}", status);
                resp.sendError(502, "SoundCloud returned " + status);
                return;
            }

            resp.setContentType("audio/mpeg");
            resp.setStatus(HttpServletResponse.SC_OK);

            // Content-Length aus der Antwort weitergeben (optional)
            response.headers().firstValue("content-length")
                    .ifPresent(len -> resp.setHeader("Content-Length", len));

            try (InputStream in = response.body();
                 OutputStream out = resp.getOutputStream()) {
                byte[] buf = new byte[STREAM_BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                }
            }
            logger.debug("MP3-Seek ab {}s (Byte {}) ausgeliefert", fromSeconds, byteOffset);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.sendError(503, "Interrupted");
        } catch (Exception e) {
            logger.warn("MP3-Seek fehlgeschlagen: {}", e.getMessage());
            resp.sendError(500, "Proxy error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // HLS m3u8 Seek (Fallback)
    // -------------------------------------------------------------------------

    private void handleHlsSeek(String m3u8Url, int fromSeconds, HttpServletResponse resp)
            throws IOException {
        logger.info("HLS-Seek: {}s aus {}", fromSeconds, m3u8Url);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(m3u8Url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/x-mpegURL, application/vnd.apple.mpegurl, */*")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("SoundCloud HLS lieferte {}", response.statusCode());
                resp.sendError(502, "SoundCloud returned " + response.statusCode());
                return;
            }

            String modified = buildSeekPlaylist(response.body(), fromSeconds);
            resp.setContentType("application/x-mpegURL");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write(modified);
            logger.debug("HLS-Seek-Playlist ab {}s ausgeliefert ({} Zeichen)", fromSeconds, modified.length());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.sendError(503, "Interrupted");
        } catch (Exception e) {
            logger.warn("HLS-Seek fehlgeschlagen: {}", e.getMessage());
            resp.sendError(500, "Proxy error: " + e.getMessage());
        }
    }

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
                if (!line.startsWith("#EXT-X-START")) {
                    headerLines.add(line);
                }
            }
        }

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

        logger.debug("m3u8: {} Segmente, starte ab Index {} ({}s)", segmentUrls.size(), startIdx, fromSeconds);
        return sb.toString();
    }

    private double parseDuration(String extinf) {
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
