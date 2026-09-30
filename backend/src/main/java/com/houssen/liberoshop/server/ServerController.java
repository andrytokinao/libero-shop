package com.houssen.liberoshop.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * How to reach this server: who it is, at which addresses, and where its mobile app is.
 */
@RestController
@RequestMapping("/api/server")
public class ServerController {

    /**
     * What the mobile app checks before saving an address: that something answers, and that it
     * is this application rather than a router's admin page on the same port.
     */
    public static final String APPLICATION = "libero-shop";

    private final ServerAddresses addresses;
    private final MobileProperties mobile;
    private final String version;

    public ServerController(ServerAddresses addresses, MobileProperties mobile,
                            ObjectProvider<BuildProperties> build) {
        this.addresses = addresses;
        this.mobile = mobile;
        BuildProperties properties = build.getIfAvailable();
        // Absent when run from the IDE without a Maven build: said, rather than invented.
        this.version = properties == null ? "dev" : properties.getVersion();
    }

    /** Public: asked before anyone has signed in, to test an address typed or scanned. */
    @GetMapping("/info")
    public ServerInfoResponse info() {
        return new ServerInfoResponse(APPLICATION, "Libero Shop", version);
    }

    /**
     * The addresses to show as QR codes on the administrator's screen.
     *
     * <p>The port is the one the request arrived on at this server, not the one in the browser's
     * address bar: behind {@code ng serve} those differ, and the phones talk to the server
     * directly. The address the administrator is browsing on comes first when it is a real name
     * rather than localhost -- "boutique.local" is a better code than an IP that DHCP may move.
     */
    @GetMapping("/connection")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ServerConnectionResponse connection(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getLocalPort();
        List<ServerConnectionResponse.Address> result = new ArrayList<>();

        String browsedHost = request.getServerName();
        if (!isLocal(browsedHost)) {
            String url = urlOf(scheme, browsedHost, request.getServerPort());
            result.add(new ServerConnectionResponse.Address(url, "Adresse utilisée par ce navigateur",
                    false, ConnectionCode.of(url)));
        }
        for (ServerAddresses.Candidate candidate : addresses.localCandidates()) {
            String url = urlOf(scheme, candidate.host(), port);
            if (result.stream().noneMatch(address -> address.url().equals(url))) {
                result.add(new ServerConnectionResponse.Address(url, candidate.interfaceName(),
                        candidate.likelyVirtual(), ConnectionCode.of(url)));
            }
        }
        return new ServerConnectionResponse(ConnectionCode.SCHEME, result, publishedApk());
    }

    /** The APK in the downloads folder, or null when none has been published yet. */
    private ServerConnectionResponse.Apk publishedApk() {
        Path file = mobile.apkPath();
        try {
            if (!Files.isRegularFile(file)) {
                return null;
            }
            return new ServerConnectionResponse.Apk(DownloadsConfiguration.PATH + mobile.apkFile(),
                    Files.size(file), Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            return null;
        }
    }

    private static String urlOf(String scheme, String host, int port) {
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    private static boolean isLocal(String host) {
        if (host == null || host.isBlank() || "localhost".equalsIgnoreCase(host)) {
            return true;
        }
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
