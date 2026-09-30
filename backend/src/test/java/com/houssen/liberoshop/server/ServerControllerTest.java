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
 * local addresses only to callers on a local network, and admits the mobile web view across
 * origins.
 */
@SpringBootTest(properties = {
        "liberoshop.security.mobile-origins=https://localhost",
        "liberoshop.mobile.public-url=https://boutique.example.com/"
})
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
    @DisplayName("shows the download page's addresses without a token, the Internet one included")
    void connectionIsPublic() throws Exception {
        mvc.perform(get("/api/server/connection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internet.url").value("https://boutique.example.com"))
                .andExpect(jsonPath("$.internet.connectionCode")
                        .value(ConnectionCode.of("https://boutique.example.com")));
    }

    @Test
    @DisplayName("keeps the shop's network addresses from a caller on the Internet")
    void localAddressesStayLocal() throws Exception {
        mvc.perform(get("/api/server/connection").with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    request.setServerName("boutique.example.com");
                    return request;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses").isEmpty())
                .andExpect(jsonPath("$.internet.url").value("https://boutique.example.com"));
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
    @DisplayName("tells the app about its pages before sign-in, and never serves a stale version")
    void mobileUpdateIsPublic() throws Exception {
        mvc.perform(get("/api/mobile/update"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").isBoolean());
        mvc.perform(get("/api/mobile/bundle/0000000000000000.zip")).andExpect(status().isNotFound());
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
