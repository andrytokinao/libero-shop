package com.houssen.liberoshop.web;

import com.houssen.liberoshop.repository.UserAppRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A session prolonged while good: a new token, the old one closed, and nothing for a closed one. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SessionRenewalTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private UserAppRepository users;

    private String tokenOf(String body) {
        return json.readTree(body).get("accessToken").asString();
    }

    private String signIn(String username) throws Exception {
        return tokenOf(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"vaha2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String renew(String token) throws Exception {
        return tokenOf(mvc.perform(post("/api/auth/renew").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.user.username").value("fatima"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("une session encore bonne est prolongee : nouveau jeton valable, l'ancien ferme")
    void prolongee() throws Exception {
        String old = signIn("fatima");

        String renewed = renew(old);

        assertNotEquals(old, renewed);
        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", "Bearer " + renewed))
                .andExpect(status().isOk());
        mvc.perform(get("/api/invoices").param("mine", "true").header("Authorization", "Bearer " + old))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("sans jeton, ou avec un compte desactive, pas de prolongation")
    void refusee() throws Exception {
        mvc.perform(post("/api/auth/renew")).andExpect(status().isUnauthorized());

        String token = signIn("fatima");
        users.findByUsername("fatima").orElseThrow().setEnabled(false);
        mvc.perform(post("/api/auth/renew").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }
}
