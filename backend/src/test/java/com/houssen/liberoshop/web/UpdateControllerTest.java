package com.houssen.liberoshop.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The update endpoints answer only the super-admin, and report the running version. Applying is
 * not exercised here: there is no delivered install under tests, so the status says so and an
 * attempt would be refused -- which is the behaviour asserted.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UpdateControllerTest {

    private static final String PASSWORD = "vaha2026";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;

    @Test
    @DisplayName("the super-admin sees the status: current version, and no in-place update here")
    void statusForTheAdmin() throws Exception {
        String admin = signIn("mparany");
        mvc.perform(get("/api/update/status").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentVersion").isNotEmpty())
                .andExpect(jsonPath("$.canApply").value(false))
                .andExpect(jsonPath("$.unavailableReason").isNotEmpty());
    }

    @Test
    @DisplayName("another role is refused the update status")
    void statusForbiddenToOthers() throws Exception {
        String cashier = signIn("hary");
        mvc.perform(get("/api/update/status").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("without a token the update status is not served")
    void statusNeedsAuthentication() throws Exception {
        mvc.perform(get("/api/update/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a non-admin cannot roll back either")
    void rollbackForbiddenToOthers() throws Exception {
        String cashier = signIn("hary");
        mvc.perform(post("/api/update/rollback").header("Authorization", bearer(cashier)))
                .andExpect(status().isForbidden());
    }

    private String signIn(String username) throws Exception {
        MvcResult login = mvc.perform(login(username, PASSWORD)).andExpect(status().isOk()).andReturn();
        return json.readTree(login.getResponse().getContentAsString()).get("accessToken").asString();
    }

    private static MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
