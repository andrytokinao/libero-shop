package com.houssen.liberoshop.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What no handler foresaw still answers in the application's format, with its proper status. */
@SpringBootTest
@AutoConfigureMockMvc
class UnexpectedErrorsTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;

    private String admin() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mparany\",\"password\":\"vaha2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    @Test
    @DisplayName("un corps illisible : 400 au format de l'application, pas celui de Spring")
    void corpsIllisible() throws Exception {
        mvc.perform(post("/api/suppliers").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{ pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_REFUSED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("une methode non permise garde son statut, dite en mots")
    void methodeNonPermise() throws Exception {
        mvc.perform(delete("/api/dashboard/admin").header("Authorization", admin()))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("REQUEST_REFUSED"));
    }

    @Test
    @DisplayName("un parametre du mauvais type : 400, pas 500")
    void parametreMalForme() throws Exception {
        mvc.perform(get("/api/stock/outputs").param("page", "abc").header("Authorization", admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_REFUSED"));
    }

    @Test
    @DisplayName("un acces refuse reste un 403 de la securite")
    void accesRefuseInchange() throws Exception {
        String cashier = "Bearer " + json.readTree(mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"fatima\",\"password\":\"vaha2026\"}"))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();

        mvc.perform(get("/api/users").header("Authorization", cashier))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
