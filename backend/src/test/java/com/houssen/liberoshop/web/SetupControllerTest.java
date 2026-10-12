package com.houssen.liberoshop.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A shipped installation: no demonstration accounts. The server opens the administrator account
 * itself, shows its password at its own computer, and stops as soon as it has been replaced.
 */
@SpringBootTest(properties = {
        "liberoshop.demo-data.enabled=false",
        "liberoshop.security.first-password-file=" + SetupControllerTest.PASSWORD_FILE
})
@AutoConfigureMockMvc
class SetupControllerTest {

    static final String PASSWORD_FILE = "target/setup-test/first-password.txt";

    private static final String SERVER = "127.0.0.1";
    private static final String PHONE = "192.168.1.42";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("premier demarrage : mot de passe de depart affiche sur le serveur, a remplacer, puis plus rien")
    void firstStart() throws Exception {
        JsonNode shown = json.readTree(mvc.perform(get("/api/setup/status").with(from(SERVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstPassword").value(true))
                .andExpect(jsonPath("$.username").value("admin"))
                .andReturn().getResponse().getContentAsString());
        String password = shown.get("password").asString();
        assertEquals(10, password.length());

        // A phone on the Wi-Fi learns that there is one, not what it is...
        mvc.perform(get("/api/setup/status").with(from(PHONE)))
                .andExpect(jsonPath("$.firstPassword").value(true))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        // ...nor does anyone arriving through a relay running on the server.
        mvc.perform(get("/api/setup/status").with(request -> {
                    request.setRemoteAddr(SERVER);
                    request.addHeader("X-Forwarded-For", PHONE);
                    return request;
                }))
                .andExpect(jsonPath("$.password").doesNotExist());
        // The form that used to create the first account is gone.
        mvc.perform(post("/api/setup/admin").with(from(SERVER)).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Un Autre","username":"autre","password":"motdepasse-long"}"""))
                .andExpect(status().is4xxClientError());

        // It signs in, as a super-admin who is asked for a password of their own.
        String token = "Bearer " + json.readTree(mvc.perform(login("admin", password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
        mvc.perform(get("/api/auth/session").header("Authorization", token))
                .andExpect(jsonPath("$.user.roles[0]").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        // Signing in is not replacing it: still shown.
        mvc.perform(get("/api/setup/status").with(from(SERVER)))
                .andExpect(jsonPath("$.password").value(password));

        mvc.perform(post("/api/auth/password").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"%s","newPassword":"mon-propre-secret"}""".formatted(password)))
                .andExpect(status().isNoContent());

        // Replaced: nothing to show any more, at the server's computer included, and no file left.
        assertTrue(Files.exists(Path.of(PASSWORD_FILE)));
        mvc.perform(get("/api/setup/status").with(from(SERVER)))
                .andExpect(jsonPath("$.firstPassword").value(false))
                .andExpect(jsonPath("$.password").doesNotExist());
        assertFalse(Files.exists(Path.of(PASSWORD_FILE)));
        mvc.perform(login("admin", password)).andExpect(status().isUnauthorized());
        mvc.perform(login("admin", "mon-propre-secret")).andExpect(status().isOk());
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}""".formatted(username, password));
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
