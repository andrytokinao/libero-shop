package com.houssen.liberoshop.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The addresses a phone on the shop's network could reach this server at.
 *
 * <p>The server cannot know which network the phones are on, so it lists every candidate and
 * lets the administrator show the right QR code. Candidates are the IPv4 addresses of the
 * private ranges (192.168.x, 10.x, 172.16-31.x) on interfaces that are up. Loopback is left out
 * -- "localhost" on a phone is the phone -- and so is IPv6, which a shop's Wi-Fi rarely routes
 * and nobody types. Virtual adapters (Hyper-V, VirtualBox, Docker, WSL) are kept but listed
 * last: they are usually not the Wi-Fi, but a guess that hides the right one would be worse.
 */
@Component
public class ServerAddresses {

    private static final Logger log = LoggerFactory.getLogger(ServerAddresses.class);

    private static final Pattern VIRTUAL = Pattern.compile(
            "vethernet|virtualbox|vmware|hyper-v|docker|wsl|vbox|virtual|loopback|tap|tun",
            Pattern.CASE_INSENSITIVE);

    /** One address of this machine on a local network. */
    public record Candidate(String host, String interfaceName, boolean likelyVirtual) {
    }

    public List<Candidate> localCandidates() {
        List<Candidate> found = new ArrayList<>();
        try {
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback()) {
                    continue;
                }
                String name = nic.getDisplayName() == null ? nic.getName() : nic.getDisplayName();
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        found.add(new Candidate(address.getHostAddress(), name,
                                nic.isVirtual() || VIRTUAL.matcher(name).find()));
                    }
                }
            }
        } catch (SocketException e) {
            // No list is not an error the caller can fix; the screen then says so.
            log.warn("Cannot list the network interfaces: {}", e.getMessage());
        }
        found.sort(Comparator.comparing(Candidate::likelyVirtual)
                .thenComparing(candidate -> candidate.interfaceName().toLowerCase(Locale.ROOT)));
        return found;
    }
}
