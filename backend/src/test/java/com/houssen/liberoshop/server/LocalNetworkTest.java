package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who counts as "in the shop": the Wi-Fi's addresses, and nobody a relay brought in from outside. */
class LocalNetworkTest {

    private static MockHttpServletRequest from(String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    @Test
    @DisplayName("private, loopback and link-local addresses are local; public ones and names are not")
    void addresses() {
        assertTrue(LocalNetwork.isLocalAddress("192.168.1.42"));
        assertTrue(LocalNetwork.isLocalAddress("10.0.0.5"));
        assertTrue(LocalNetwork.isLocalAddress("127.0.0.1"));
        assertTrue(LocalNetwork.isLocalAddress("fe80::1"));
        assertTrue(LocalNetwork.isLocalAddress("fd12:3456::7"));
        assertFalse(LocalNetwork.isLocalAddress("203.0.113.7"));
        assertFalse(LocalNetwork.isLocalAddress("2001:db8::1"));
        assertFalse(LocalNetwork.isLocalAddress("unknown"));
        assertFalse(LocalNetwork.isLocalAddress("localhost"), "a name is never looked up");
        assertFalse(LocalNetwork.isLocalAddress(null));
    }

    @Test
    @DisplayName("a phone on the Wi-Fi is local; a caller from the Internet is not")
    void directCallers() {
        assertTrue(LocalNetwork.isLocalCaller(from("192.168.1.42")));
        assertFalse(LocalNetwork.isLocalCaller(from("203.0.113.7")));
    }

    @Test
    @DisplayName("behind a relay in the shop, the real caller named by the relay decides")
    void forwardedCallers() {
        MockHttpServletRequest tunnelled = from("127.0.0.1");
        tunnelled.addHeader("X-Forwarded-For", "203.0.113.7, 192.168.1.1");
        assertFalse(LocalNetwork.isLocalCaller(tunnelled));

        MockHttpServletRequest standard = from("127.0.0.1");
        standard.addHeader("Forwarded", "for=\"[2001:db8::1]:4711\";proto=https");
        assertFalse(LocalNetwork.isLocalCaller(standard));

        MockHttpServletRequest realIp = from("10.0.0.2");
        realIp.addHeader("X-Real-IP", "198.51.100.4");
        assertFalse(LocalNetwork.isLocalCaller(realIp));

        MockHttpServletRequest localRelay = from("127.0.0.1");
        localRelay.addHeader("X-Forwarded-For", "192.168.1.42");
        localRelay.addHeader("Forwarded", "for=192.168.1.42:51000");
        assertTrue(LocalNetwork.isLocalCaller(localRelay));
    }
}
