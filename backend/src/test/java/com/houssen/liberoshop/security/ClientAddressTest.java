package com.houssen.liberoshop.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which device a sign-in attempt is counted against: the connection's address, unless the
 * connection is a reverse proxy the installation declared.
 */
class ClientAddressTest {

    private static final String PROXY = "10.0.0.2";
    private static final String PHONE = "192.168.1.42";

    private final ClientAddress direct = new ClientAddress(new SecurityProperties(null, null, null));
    private final ClientAddress behindProxy =
            new ClientAddress(new SecurityProperties(null, null, List.of(PROXY, "::1")));

    private static MockHttpServletRequest request(String remote, String... forwardedFor) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remote);
        for (String value : forwardedFor) {
            request.addHeader("X-Forwarded-For", value);
        }
        return request;
    }

    @Test
    @DisplayName("Sans proxy declare, l'en-tete n'est pas lu : on ne se fait pas passer pour un autre")
    void headerIgnoredWithoutATrustedProxy() {
        assertEquals(PHONE, direct.of(request(PHONE)));
        assertEquals(PHONE, direct.of(request(PHONE, "203.0.113.9")));
        // A proxy nobody declared is a caller like another.
        assertEquals(PROXY, direct.of(request(PROXY, PHONE)));
    }

    @Test
    @DisplayName("Derriere le proxy declare, chaque appareil est compte pour lui-meme")
    void deviceReadBehindTheTrustedProxy() {
        assertEquals(PHONE, behindProxy.of(request(PROXY, PHONE)));
        assertEquals("192.168.1.43", behindProxy.of(request(PROXY, "192.168.1.43")));
        // Still the connection's address for whoever does not come through the proxy.
        assertEquals(PHONE, behindProxy.of(request(PHONE, "203.0.113.9")));
    }

    @Test
    @DisplayName("Ce que l'appelant ecrit a gauche de l'en-tete ne compte pas : le proxy ecrit a droite")
    void forgedEntriesAreNotBelieved() {
        assertEquals(PHONE, behindProxy.of(request(PROXY, "203.0.113.9, " + PHONE)));
        assertEquals(PHONE, behindProxy.of(request(PROXY, "203.0.113.9", PHONE)));
        // Two proxies in a row, both declared: the device is the one before them.
        assertEquals(PHONE, behindProxy.of(request("0:0:0:0:0:0:0:1", PHONE + ", " + PROXY)));
    }

    @Test
    @DisplayName("Un en-tete absent ou illisible laisse l'adresse du proxy")
    void unreadableHeaderFallsBackToTheProxy() {
        assertEquals(PROXY, behindProxy.of(request(PROXY)));
        assertEquals(PROXY, behindProxy.of(request(PROXY, "unknown")));
        assertEquals(PROXY, behindProxy.of(request(PROXY, PHONE + ", pas-une-adresse")));
    }
}
