package org.openhab.binding.soundcloud.internal;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.function.Consumer;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@NonNullByDefault
public class SoundCloudCallbackServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private final Logger logger = LoggerFactory.getLogger(SoundCloudCallbackServlet.class);
    private final Consumer<String> onCodeReceived;

    public SoundCloudCallbackServlet(Consumer<String> onCodeReceived) {
        this.onCodeReceived = onCodeReceived;
    }

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws IOException {
        if (req == null || resp == null) return;

        String code = req.getParameter("code");
        String error = req.getParameter("error");

        resp.setContentType("text/html; charset=utf-8");
        resp.setStatus(HttpServletResponse.SC_OK);

        try (PrintWriter out = resp.getWriter()) {
            if (code != null && !code.isBlank()) {
                out.println("<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
                        + "<h2>&#10003; SoundCloud erfolgreich verbunden!</h2>"
                        + "<p>Du kannst dieses Fenster schliessen. openHAB ist jetzt autorisiert.</p>"
                        + "</body></html>");
                logger.info("OAuth-Code empfangen — tausche gegen Token");
                onCodeReceived.accept(code);
            } else {
                out.println("<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
                        + "<h2>&#10007; Autorisierung fehlgeschlagen</h2>"
                        + "<p>Fehler: " + (error != null ? error : "Kein Code empfangen") + "</p>"
                        + "<p>Bitte erneut versuchen.</p>"
                        + "</body></html>");
                logger.warn("OAuth-Callback ohne Code empfangen. Fehler: {}", error);
            }
        }
    }
}
