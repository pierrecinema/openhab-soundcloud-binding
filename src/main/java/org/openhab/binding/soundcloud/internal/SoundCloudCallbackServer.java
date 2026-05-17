package org.openhab.binding.soundcloud.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.function.Consumer;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpServer;

@NonNullByDefault
public class SoundCloudCallbackServer {

    private static final String CALLBACK_PATH = "/api/platforms/soundcloud/oauth-callback";
    private static final String SUCCESS_HTML =
            "<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
            + "<h2>&#10003; SoundCloud erfolgreich verbunden!</h2>"
            + "<p>Du kannst dieses Fenster schliessen.</p>"
            + "</body></html>";
    private static final String ERROR_HTML =
            "<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
            + "<h2>&#10007; Kein Code empfangen</h2>"
            + "<p>Bitte erneut versuchen.</p>"
            + "</body></html>";

    private final Logger logger = LoggerFactory.getLogger(SoundCloudCallbackServer.class);
    private final int port;
    private final Consumer<String> onCodeReceived;
    private @Nullable HttpServer server;

    public SoundCloudCallbackServer(int port, Consumer<String> onCodeReceived) {
        this.port = port;
        this.onCodeReceived = onCodeReceived;
    }

    public void start() throws IOException {
        HttpServer srv = HttpServer.create(new InetSocketAddress(port), 0);
        srv.createContext(CALLBACK_PATH, exchange -> {
            String code = parseCode(exchange.getRequestURI().getRawQuery());
            if (code != null && !code.isBlank()) {
                send(exchange, 200, SUCCESS_HTML);
                logger.info("OAuth-Code empfangen — tausche gegen Token");
                onCodeReceived.accept(code);
            } else {
                send(exchange, 400, ERROR_HTML);
                logger.warn("Callback ohne Code empfangen: {}", exchange.getRequestURI());
            }
        });
        srv.setExecutor(null);
        srv.start();
        server = srv;
        logger.debug("OAuth-Callback-Server gestartet auf Port {}", port);
    }

    public void stop() {
        HttpServer srv = server;
        if (srv != null) {
            srv.stop(0);
            server = null;
            logger.debug("OAuth-Callback-Server gestoppt");
        }
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static @Nullable String parseCode(@Nullable String query) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "code".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
