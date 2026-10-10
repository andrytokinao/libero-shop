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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public downloads folder hands out the Android app and nothing else, whatever is left
 * beside it.
 */
@SpringBootTest(properties = "liberoshop.mobile.downloads-dir=" + DownloadsTest.FOLDER)
@AutoConfigureMockMvc
class DownloadsTest {

    static final String FOLDER = "target/downloads-test";

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void anApkAndABackupBesideIt() throws Exception {
        Path folder = Path.of(FOLDER);
        Files.createDirectories(folder.resolve("sauvegardes"));
        Files.writeString(folder.resolve("libero-shop.apk"), "apk");
        Files.writeString(folder.resolve("libertyshop-2026-10-10.zip"), "base");
        Files.writeString(folder.resolve("sauvegardes").resolve("libertyshop.zip"), "base");
    }

    @Test
    @DisplayName("L'application mobile se telecharge sans connexion")
    void servesTheApk() throws Exception {
        mvc.perform(get("/downloads/libero-shop.apk"))
                .andExpect(status().isOk())
                .andExpect(content().string("apk"));
    }

    @Test
    @DisplayName("Une sauvegarde posee dans le dossier public n'est pas telechargeable")
    void servesNothingElse() throws Exception {
        mvc.perform(get("/downloads/libertyshop-2026-10-10.zip")).andExpect(status().isNotFound());
        mvc.perform(get("/downloads/sauvegardes/libertyshop.zip")).andExpect(status().isNotFound());
        // Refused before it reaches the folder at all.
        mvc.perform(get("/downloads/sauvegardes/../libertyshop-2026-10-10.zip"))
                .andExpect(status().is4xxClientError());
    }
}
