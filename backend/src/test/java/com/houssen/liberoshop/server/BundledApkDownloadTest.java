package com.houssen.liberoshop.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A fresh installation: nothing was ever copied into the downloads folder, and the phones still
 * get the app, the one delivered with the installer.
 */
@SpringBootTest(properties = {
        "liberoshop.mobile.downloads-dir=" + BundledApkDownloadTest.EMPTY_FOLDER,
        "liberoshop.mobile.bundled-apk=" + BundledApkDownloadTest.INSTALLED + "/libero-shop.apk"
})
@AutoConfigureMockMvc
class BundledApkDownloadTest {

    static final String EMPTY_FOLDER = "target/bundled-apk-test/downloads";
    static final String INSTALLED = "target/bundled-apk-test/app";

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void onlyTheInstallersApk() throws Exception {
        Files.createDirectories(Path.of(EMPTY_FOLDER));
        Files.deleteIfExists(Path.of(EMPTY_FOLDER, "libero-shop.apk"));
        Path installed = Files.createDirectories(Path.of(INSTALLED));
        Files.writeString(installed.resolve("libero-shop.apk"), "apk livree");
        Files.writeString(installed.resolve("liberoshop.jar"), "serveur");
    }

    @Test
    @DisplayName("L'APK livree avec l'installateur se telecharge, et la page l'annonce")
    void servesTheInstallersApk() throws Exception {
        mvc.perform(get("/downloads/libero-shop.apk"))
                .andExpect(status().isOk())
                .andExpect(content().string("apk livree"));
        mvc.perform(get("/api/server/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apk.path").value("/downloads/libero-shop.apk"))
                .andExpect(jsonPath("$.apk.sizeBytes").value(10));
    }

    @Test
    @DisplayName("Rien d'autre du dossier de l'application n'est telechargeable")
    void servesNothingElseOfTheApplicationFolder() throws Exception {
        mvc.perform(get("/downloads/liberoshop.jar")).andExpect(status().isNotFound());
    }
}
