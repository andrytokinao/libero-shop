package com.houssen.liberoshop.server;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * What a phone meets before it has an account: the server says who it is to anyone, lists its
 * addresses only to an administrator, and admits the mobile web view across origins.
 */
@SpringBootTest(properties = "liberoshop.security.mobile-origins=https://localhost")
@AutoConfigureMockMvc
class ServerControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("answers who it is without a token, so an address can be tested before sign-in")
    void infoIsPublic() throws Exception {
        mvc.perform(get("/api/server/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value(ServerController.APPLICATION));
    }

    @Test
    @DisplayName("keeps its network addresses for signed-in administrators")
    void connectionNeedsAnAccount() throws Exception {
        mvc.perform(get("/api/server/connection")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lets the mobile app's web view call across origins, and nobody else")
    void mobileOriginAdmitted() throws Exception {
        mvc.perform(options("/api/server/info")
                        .header(HttpHeaders.ORIGIN, "https://localhost")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://localhost"));

        mvc.perform(options("/api/server/info")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an APK not published yet is a 404, never the Angular page saved as an .apk")
    void missingApkIsNotFound() throws Exception {
        mvc.perform(get("/downloads/libero-shop-absent.apk")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("encodes the address in a code only this app takes for a server")
    void connectionCode() {
        assertEquals("liberoshop://connect?server=http%3A%2F%2F192.168.1.10%3A8080",
                ConnectionCode.of("http://192.168.1.10:8080"));
    }
}
