package com.houssen.liberoshop.update;

import com.houssen.liberoshop.service.exception.BusinessRuleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Talks to the configured update URL: fetches the manifest, and downloads the {@code .lsu} it
 * points at. The JDK's own HTTP client, so no dependency is added for it.
 *
 * <p>Every online operation is refused when no URL is configured -- the manual upload page does
 * not need one, and an install that was never given a URL must not reach out.
 */
@Component
public class UpdateSource {

    private static final Logger log = LoggerFactory.getLogger(UpdateSource.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MANIFEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofMinutes(10);

    private final UpdateProperties properties;
    private final HttpClient client;

    public UpdateSource(UpdateProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** Reads the manifest at the configured URL. Throws a clear refusal when none is configured. */
    public RemoteUpdateInfo fetchManifest() {
        if (!properties.hasUrl()) {
            throw new BusinessRuleException("UPDATE_NO_URL",
                    "Aucune adresse de mise a jour n'est configuree.");
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(properties.url()))
                .timeout(MANIFEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<byte[]> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new BusinessRuleException("UPDATE_UNREACHABLE",
                    "Le serveur de mise a jour est injoignable : " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("UPDATE_UNREACHABLE", "Verification interrompue.");
        }
        if (response.statusCode() != 200) {
            throw new BusinessRuleException("UPDATE_UNREACHABLE",
                    "Le serveur de mise a jour a repondu " + response.statusCode() + ".");
        }
        return RemoteUpdateInfo.parse(response.body());
    }

    /** Downloads the {@code .lsu} to a temporary file. The caller verifies and then applies it. */
    public Path download(RemoteUpdateInfo info) {
        Path temp;
        try {
            temp = Files.createTempFile("liberoshop-download-", ".lsu");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(info.downloadUrl()))
                .timeout(DOWNLOAD_TIMEOUT)
                .GET()
                .build();
        try {
            HttpResponse<Path> response = client.send(request,
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(temp);
                throw new BusinessRuleException("UPDATE_UNREACHABLE",
                        "Le telechargement de la mise a jour a echoue (" + response.statusCode() + ").");
            }
            log.info("Mise a jour {} telechargee depuis {}.", info.latestVersion(), info.downloadUrl());
            return response.body();
        } catch (IOException e) {
            deleteQuietly(temp);
            throw new BusinessRuleException("UPDATE_UNREACHABLE",
                    "Le telechargement de la mise a jour a echoue : " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(temp);
            throw new BusinessRuleException("UPDATE_UNREACHABLE", "Telechargement interrompu.");
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // A leftover temp file is harmless.
        }
    }
}
