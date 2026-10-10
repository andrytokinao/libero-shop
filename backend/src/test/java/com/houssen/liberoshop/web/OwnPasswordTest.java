package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A password is its holder's: the one an administrator hands out is to be replaced at the first
 * sign-in, and anyone can replace their own -- knowing the current one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OwnPasswordTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper json = new ObjectMapper();

    private String owner;
    private String newcomer;

    @BeforeEach
    void anOwner() {
        owner = "g" + UUID.randomUUID().toString().substring(0, 8);
        newcomer = "n" + UUID.randomUUID().toString().substring(0, 8);
        users.save(UserApp.builder().fullName("Gerant").username(owner)
                .password(passwordEncoder.encode("gerant-secret")).enabled(true)
                .roles(EnumSet.of(RoleApp.SUPER_ADMIN)).build());
    }

    @Test
    @DisplayName("Un compte ouvert par l'administrateur doit choisir son mot de passe, puis n'y est plus invite")
    void handedOutPasswordIsReplacedByItsHolder() throws Exception {
        String admin = tokenOf(owner, "gerant-secret");
        // The administrator's own was not handed out by anyone.
        mvc.perform(get("/api/auth/session").header("Authorization", admin))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // Seven characters are no longer enough...
        mvc.perform(creation(newcomer, "sept777").header("Authorization", admin))
                .andExpect(status().isBadRequest());
        mvc.perform(creation(newcomer, "donne-par-admin").header("Authorization", admin))
                .andExpect(status().isCreated());

        String first = tokenOf(newcomer, "donne-par-admin");
        mvc.perform(get("/api/auth/session").header("Authorization", first))
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        // ...and the current one has to be known, the new one to be another and long enough.
        mvc.perform(change("pas-le-bon", "mon-propre-secret").header("Authorization", first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WRONG_PASSWORD"));
        mvc.perform(change("donne-par-admin", "donne-par-admin").header("Authorization", first))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_PASSWORD"));
        mvc.perform(change("donne-par-admin", "court1").header("Authorization", first))
                .andExpect(status().isBadRequest());

        mvc.perform(change("donne-par-admin", "mon-propre-secret").header("Authorization", first))
                .andExpect(status().isNoContent());

        // The session that changed it is closed with the others; the old password opens nothing.
        mvc.perform(get("/api/auth/session").header("Authorization", first))
                .andExpect(status().isUnauthorized());
        mvc.perform(login(newcomer, "donne-par-admin")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session").header("Authorization", tokenOf(newcomer, "mon-propre-secret")))
                .andExpect(jsonPath("$.mustChangePassword").value(false));

        // Handed a new one by the administrator, the holder is asked again.
        Long id = users.findByUsername(newcomer).orElseThrow().getId();
        mvc.perform(put("/api/users/" + id + "/password").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"redonne-par-admin"}"""))
                .andExpect(status().isOk());
        mvc.perform(get("/api/auth/session").header("Authorization", tokenOf(newcomer, "redonne-par-admin")))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("Sans connexion, pas de changement de mot de passe")
    void anonymousIsRefused() throws Exception {
        mvc.perform(change("gerant-secret", "un-autre-secret")).andExpect(status().isUnauthorized());
    }

    private String tokenOf(String username, String password) throws Exception {
        String body = mvc.perform(login(username, password))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"username":"%s","password":"%s"}""".formatted(username, password));
    }

    private static MockHttpServletRequestBuilder creation(String username, String password) {
        return post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Nouveau","username":"%s","password":"%s","roles":["CASHIER"]}"""
                        .formatted(username, password));
    }

    private static MockHttpServletRequestBuilder change(String current, String next) {
        return post("/api/auth/password").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"currentPassword":"%s","newPassword":"%s"}""".formatted(current, next));
    }
}
