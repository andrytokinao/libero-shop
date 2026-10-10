package com.houssen.liberoshop.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The device a request really comes from, for what is counted per device -- the sign-in
 * attempts of {@link LoginThrottle}.
 *
 * <p>Directly reached, that is the address of the connection. Behind a reverse proxy every
 * connection comes from the proxy, and the real caller is named in {@code X-Forwarded-For}: one
 * person guessing passwords would then slow everybody down, and nobody in particular. So when,
 * and only when, the connection comes from an address listed in
 * {@code liberoshop.security.trusted-proxies}, the header is read.
 *
 * <p>It is read from the right. A proxy appends the address it saw to whatever the caller sent,
 * so the left of the header is the caller's to forge and the right is the proxy's word: the
 * last entry that is not itself a trusted proxy is the device. A caller reaching the server
 * directly can put anything in the header -- it is not even looked at.
 */
@Component
public class ClientAddress {

    /** Digits and dots, or hex digits and colons: an address, never a name to look up. */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9.]+|[0-9a-fA-F:]*:[0-9a-fA-F:.]*");

    private final Set<String> trustedProxies;

    public ClientAddress(SecurityProperties security) {
        this.trustedProxies = security.trustedProxies().stream()
                .map(ClientAddress::normalized)
                .collect(Collectors.toUnmodifiableSet());
    }

    public String of(HttpServletRequest request) {
        String remote = normalized(request.getRemoteAddr());
        if (!trustedProxies.contains(remote)) {
            return remote;
        }
        List<String> hops = new ArrayList<>();
        Enumeration<String> headers = request.getHeaders("X-Forwarded-For");
        while (headers != null && headers.hasMoreElements()) {
            for (String hop : headers.nextElement().split(",")) {
                if (!hop.isBlank()) {
                    hops.add(hop.trim());
                }
            }
        }
        for (int index = hops.size() - 1; index >= 0; index--) {
            String hop = hops.get(index);
            if (!IP_LITERAL.matcher(hop).matches()) {
                // Not an address: the chain cannot be followed further, the proxy answers for it.
                break;
            }
            String address = normalized(hop);
            if (!trustedProxies.contains(address)) {
                return address;
            }
        }
        return remote;
    }

    /** One spelling per address, so {@code ::1} and {@code 0:0:0:0:0:0:0:1} are the same proxy. */
    private static String normalized(String address) {
        String trimmed = address == null ? "" : address.trim();
        if (!IP_LITERAL.matcher(trimmed).matches()) {
            return trimmed;
        }
        try {
            // An IP literal: parsed, never looked up.
            return InetAddress.getByName(trimmed).getHostAddress();
        } catch (UnknownHostException e) {
            return trimmed;
        }
    }
}
