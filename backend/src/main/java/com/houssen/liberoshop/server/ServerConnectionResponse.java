package com.houssen.liberoshop.server;

import java.time.Instant;
import java.util.List;

/**
 * The addresses phones could reach this server at, and the app to install.
 *
 * @param scheme    the URI scheme of the connection codes, so the client checks the same one
 * @param addresses on the shop's network, best guess first. Empty when the machine has no private
 *                  IPv4 address, and also when the caller is not on a local network itself: a
 *                  visitor from the Internet has no use for them and no business mapping them
 * @param internet  the address configured for phones away from the shop, or null when the server
 *                  is not published on the Internet
 * @param apk       the published APK, or null when none has been copied to the downloads folder
 */
public record ServerConnectionResponse(String scheme, List<Address> addresses, Address internet, Apk apk) {

    /**
     * @param label          where the address comes from: a network card's name, the address
     *                       the caller is browsing on, or the Internet
     * @param likelyVirtual  a virtual adapter's, most likely not the shop's Wi-Fi
     * @param connectionCode what the QR code for this address encodes
     */
    public record Address(String url, String label, boolean likelyVirtual, String connectionCode) {
    }

    /**
     * @param path      relative to a server address, e.g. {@code /downloads/libero-shop.apk}
     * @param updatedAt when the file was last replaced, so anyone can tell which build the phones
     *                  will get
     */
    public record Apk(String path, long sizeBytes, Instant updatedAt) {
    }
}
