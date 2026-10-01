package com.houssen.liberoshop.server;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether a caller is on the shop's own network.
 *
 * <p>An address is local when it is loopback, private (10/8, 172.16/12, 192.168/16, fc00::/7) or
 * link-local. Only IP literals are judged: anything else -- "unknown", an obfuscated proxy token,
 * a host name -- counts as not local, and is never looked up.
 */
public final class LocalNetwork {

    /** Digits and dots, or hex digits and colons (with an optional dotted IPv4 tail). */
    private static final Pattern IP_LITERAL = Pattern.compile("[0-9.]+|[0-9a-fA-F:]*:[0-9a-fA-F:.]*");

    private LocalNetwork() {
    }

    public static boolean isLocalAddress(String address) {
        if (address == null || address.isBlank() || !IP_LITERAL.matcher(address.trim()).matches()) {
            return false;
        }
        try {
            // An IP literal, checked above: parsed, never looked up.
            InetAddress parsed = InetAddress.getByName(address.trim());
            byte[] bytes = parsed.getAddress();
            boolean uniqueLocalV6 = bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
            return parsed.isLoopbackAddress() || parsed.isSiteLocalAddress()
                    || parsed.isLinkLocalAddress() || uniqueLocalV6;
        } catch (UnknownHostException e) {
            return false;
        }
    }

    /**
     * True when the caller and every hop it came through are local.
     *
     * <p>Behind a relay running in the shop -- a tunnel, a reverse proxy -- every request seems to
     * come from the shop's network; the relay names the real caller in {@code X-Forwarded-For},
     * {@code X-Real-IP} or {@code Forwarded}, so those are judged too. Trusting them is safe in
     * this direction: a caller adding one of these headers can only make itself look less local,
     * never more.
     */
    public static boolean isLocalCaller(HttpServletRequest request) {
        if (!isLocalAddress(request.getRemoteAddr())) {
            return false;
        }
        return forwardedAddresses(request).stream().allMatch(LocalNetwork::isLocalAddress);
    }

    private static List<String> forwardedAddresses(HttpServletRequest request) {
        List<String> found = new ArrayList<>();
        for (String header : List.of("X-Forwarded-For", "X-Real-IP")) {
            var values = request.getHeaders(header);
            while (values != null && values.hasMoreElements()) {
                for (String part : values.nextElement().split(",")) {
                    if (!part.isBlank()) {
                        found.add(part.trim());
                    }
                }
            }
        }
        var forwarded = request.getHeaders("Forwarded");
        while (forwarded != null && forwarded.hasMoreElements()) {
            for (String element : forwarded.nextElement().split("[,;]")) {
                String pair = element.trim();
                if (pair.toLowerCase(Locale.ROOT).startsWith("for=")) {
                    found.add(nodeAddress(pair.substring(4)));
                }
            }
        }
        return found;
    }

    /** {@code "[2001:db8::1]:4711"} -> {@code 2001:db8::1}; {@code 192.0.2.60:80} -> {@code 192.0.2.60}. */
    private static String nodeAddress(String node) {
        String value = node.trim().replace("\"", "");
        if (value.startsWith("[")) {
            int end = value.indexOf(']');
            return end > 0 ? value.substring(1, end) : value;
        }
        int colon = value.indexOf(':');
        return colon > 0 && colon == value.lastIndexOf(':') ? value.substring(0, colon) : value;
    }
}
