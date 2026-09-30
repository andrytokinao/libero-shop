package com.houssen.liberoshop.server;

import java.time.Instant;
import java.util.List;

/**
 * The addresses phones could reach this server at, best guess first, and the app to install.
 *
 * @param scheme    the URI scheme of the connection codes, so the client checks the same one
 * @param addresses empty when the machine has no private IPv4 address at all -- the screen then
 *                  says the server is not on a local network
 * @param apk       the published APK, or null when none has been copied to the downloads folder
 */
public record ServerConnectionResponse(String scheme, List<Address> addresses, Apk apk) {

    /**
     * @param label          where the address comes from: a network card's name, or the address
     *                       the administrator is browsing on
     * @param likelyVirtual  a virtual adapter's, most likely not the shop's Wi-Fi
     * @param connectionCode what the QR code for this address encodes
     */
    public record Address(String url, String label, boolean likelyVirtual, String connectionCode) {
    }

    /**
     * @param path      relative to a server address, e.g. {@code /downloads/libero-shop.apk}
     * @param updatedAt when the file was last replaced, so the administrator can tell which build
     *                  the phones will get
     */
    public record Apk(String path, long sizeBytes, Instant updatedAt) {
    }
}
