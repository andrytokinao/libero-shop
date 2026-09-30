package com.houssen.liberoshop.server;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * What the QR code shown to the phones says.
 *
 * <p>{@code liberoshop://connect?server=<url>} rather than the bare URL: a phone scanning a code
 * meant for a web browser -- a menu, a Wi-Fi code -- must not take it for a server address, and
 * the scheme is how the app tells. The app still accepts a plain {@code http(s)://} URL typed or
 * pasted by hand; the scheme only guards the scan.
 */
public final class ConnectionCode {

    public static final String SCHEME = "liberoshop";

    private ConnectionCode() {
    }

    public static String of(String serverUrl) {
        return SCHEME + "://connect?server=" + URLEncoder.encode(serverUrl, StandardCharsets.UTF_8);
    }
}
