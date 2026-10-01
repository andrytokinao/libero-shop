package com.houssen.liberoshop.server;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
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
     * The addresses to show as QR codes on the download page: on the shop's network, and on the
     * Internet when one is configured.
     *
     * <p>Public, like the APK itself: the page is how a new employee equips a phone, before any
     * account exists for them. The local addresses are only listed to a caller that is itself on
     * a local network -- someone who found the server from the Internet gets the Internet address
     * and nothing about the shop's network.
     *
     * <p>The port is the one the request arrived on at this server, not the one in the browser's
     * address bar: behind {@code ng serve} those differ, and the phones talk to the server
     * directly. The address the caller is browsing on comes first when it is a real name rather
     * than localhost -- "boutique.local" is a better code than an IP that DHCP may move.
     */
    @GetMapping("/connection")
    public ServerConnectionResponse connection(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getLocalPort();
        String publicUrl = mobile.publicUrl();
        List<ServerConnectionResponse.Address> result = new ArrayList<>();

        if (LocalNetwork.isLocalAddress(request.getRemoteAddr())) {
            String browsedHost = request.getServerName();
            String browsedUrl = urlOf(scheme, browsedHost, request.getServerPort());
            if (!isLocal(browsedHost) && !browsedUrl.equals(publicUrl)) {
                result.add(new ServerConnectionResponse.Address(browsedUrl,
                        "Adresse utilisée par ce navigateur", false, ConnectionCode.of(browsedUrl)));
            }
            for (ServerAddresses.Candidate candidate : addresses.localCandidates()) {
                String url = urlOf(scheme, candidate.host(), port);
                if (result.stream().noneMatch(address -> address.url().equals(url))) {
                    result.add(new ServerConnectionResponse.Address(url, candidate.interfaceName(),
                            candidate.likelyVirtual(), ConnectionCode.of(url)));
                }
            }
        }
        ServerConnectionResponse.Address internet = publicUrl == null ? null
                : new ServerConnectionResponse.Address(publicUrl, "Internet", false, ConnectionCode.of(publicUrl));
        return new ServerConnectionResponse(ConnectionCode.SCHEME, result, internet, publishedApk());
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
