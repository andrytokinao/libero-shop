package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.RoleApp;
import com.houssen.liberoshop.entity.UserApp;
import com.houssen.liberoshop.repository.UserAppRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.UUID;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The licence status is public, for the login page's banner: a stranger learns whether the shop
 * can work, not on which machine or for whom.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LicenseStatusPrivacyTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserAppRepository users;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("Sans connexion : l'etat et les jours restants, pas l'empreinte du poste ni le detail")
    void strangersGetTheStateOnly() throws Exception {
        mvc.perform(get("/api/license/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").isNotEmpty())
                .andExpect(jsonPath("$.writesAllowed").isBoolean())
                .andExpect(jsonPath("$.daysUntilExpiry").isNumber())
                .andExpect(jsonPath("$.machineFingerprint").value(emptyString()))
                .andExpect(jsonPath("$.customerName").doesNotExist())
                .andExpect(jsonPath("$.customerId").doesNotExist())
                .andExpect(jsonPath("$.plan").doesNotExist())
                .andExpect(jsonPath("$.integrityWarning").doesNotExist())
                .andExpect(jsonPath("$.message").value("Connectez-vous pour voir le detail de la licence."));
    }

    @Test
    @DisplayName("Connecte : le detail complet, empreinte comprise")
    void signedInAccountsGetTheDetail() throws Exception {
        String handle = "l" + UUID.randomUUID().toString().substring(0, 8);
        users.save(UserApp.builder().fullName("Caisse").username(handle)
                .password(passwordEncoder.encode("caisse-secret")).enabled(true)
                .roles(EnumSet.of(RoleApp.CASHIER)).build());
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"caisse-secret"}""".formatted(handle)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = new ObjectMapper().readTree(body).get("accessToken").asString();

        mvc.perform(get("/api/license/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.machineFingerprint").value(not(emptyString())));
    }
}
