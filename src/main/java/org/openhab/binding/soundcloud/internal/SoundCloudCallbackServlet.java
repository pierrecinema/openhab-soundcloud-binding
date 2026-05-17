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
        logger.debug("OAuth-Callback empfangen — URL: {} QueryString: {} Headers: Referer={}",
                req.getRequestURL(), req.getQueryString(), req.getHeader("Referer"));

        resp.setContentType("text/html; charset=utf-8");
        resp.setStatus(HttpServletResponse.SC_OK);

        try (PrintWriter out = resp.getWriter()) {
            if (code != null && !code.isBlank()) {
                // Code arrived server-side — normal OAuth redirect worked
                out.println("<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
                        + "<h2>&#10003; SoundCloud erfolgreich verbunden!</h2>"
                        + "<p>Du kannst dieses Fenster schliessen. openHAB ist jetzt autorisiert.</p>"
                        + "</body></html>");
                logger.info("OAuth-Code empfangen — tausche gegen Token");
                onCodeReceived.accept(code);
            } else if (error != null) {
                out.println("<html><body style='font-family:sans-serif;text-align:center;margin-top:80px'>"
                        + "<h2>&#10007; Autorisierung fehlgeschlagen</h2>"
                        + "<p>Fehler: " + error + "</p>"
                        + "<p>Bitte erneut versuchen.</p>"
                        + "</body></html>");
                logger.warn("OAuth-Callback mit Fehler: {}", error);
            } else {
                // No query params — browser may have stripped them on HTTPS→HTTP redirect.
                // Deliver a JS page that reads the code from window.location and POSTs it.
                out.println("<!DOCTYPE html><html><head><meta charset='utf-8'></head><body>"
                        + "<p style='font-family:sans-serif;text-align:center;margin-top:80px'>Verarbeite...</p>"
                        + "<script>"
                        + "var p=new URLSearchParams(window.location.search);"
                        + "var h=new URLSearchParams(window.location.hash.replace('#','?'));"
                        + "var code=p.get('code')||h.get('code');"
                        + "var err=p.get('error')||h.get('error');"
                        + "if(code){"
                        + "  fetch('/soundcloud/callback?code='+encodeURIComponent(code)).then(function(r){return r.text()}).then(function(t){document.body.innerHTML=t});"
                        + "}else if(err){"
                        + "  document.body.innerHTML=\"<div style='font-family:sans-serif;text-align:center;margin-top:80px'><h2>&#10007; Fehler: \"+err+\"</h2></div>\";"
                        + "}else{"
                        + "  document.body.innerHTML=\"<div style='font-family:sans-serif;text-align:center;margin-top:80px'><h2>&#10007; Kein Code empfangen</h2><p>Bitte erneut versuchen.</p></div>\";"
                        + "}"
                        + "</script></body></html>");
                logger.warn("OAuth-Callback ohne Query-Parameter — JS-Fallback geliefert. Fehler: {}", error);
            }
        }
    }

    @Override
    protected void doPost(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws IOException {
        // Some browsers or proxies may POST instead of GET — handle the same way
        doGet(req, resp);
    }
}
