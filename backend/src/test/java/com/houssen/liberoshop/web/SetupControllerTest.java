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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A shipped installation: no demonstration accounts, so nobody to sign in as. The first account
 * is created from the server's own computer, once, and then signs in like any other.
 */
@SpringBootTest(properties = "liberoshop.demo-data.enabled=false")
@AutoConfigureMockMvc
class SetupControllerTest {

    private static final String OWNER = """
            {"fullName":"Rivo Rakoto","username":"Rivo","password":"motdepasse-long"}""";

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("premier demarrage : le compte se cree sur le serveur, une fois, puis se connecte")
    void firstStart() throws Exception {
        // Nobody yet: the form is offered at the server's computer, explained elsewhere.
        mvc.perform(get("/api/setup/status").with(from("127.0.0.1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.allowedHere").value(true));
        mvc.perform(get("/api/setup/status").with(from("192.168.1.42")))
                .andExpect(jsonPath("$.required").value(true))
                .andExpect(jsonPath("$.allowedHere").value(false));

        // A phone on the Wi-Fi does not get to make itself administrator...
        mvc.perform(creation(OWNER).with(from("192.168.1.42")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ONLY_ON_SERVER"));
        // ...nor does anyone arriving through a relay running on the server.
        mvc.perform(creation(OWNER).with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    request.addHeader("X-Forwarded-For", "192.168.1.42");
                    return request;
                }))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ONLY_ON_SERVER"));
        // The account that opens everything is not protected by six characters.
        mvc.perform(creation("""
                        {"fullName":"Rivo Rakoto","username":"rivo","password":"court1"}""")
                        .with(from("127.0.0.1")))
                .andExpect(status().isBadRequest());

        mvc.perform(creation(OWNER).with(from("127.0.0.1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("rivo"))
                .andExpect(jsonPath("$.roles[0]").value("SUPER_ADMIN"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"rivo","password":"motdepasse-long"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        // The door is closed for good, at the server's computer included.
        mvc.perform(get("/api/setup/status").with(from("127.0.0.1")))
                .andExpect(jsonPath("$.required").value(false))
                .andExpect(jsonPath("$.allowedHere").value(false));
        mvc.perform(creation("""
                        {"fullName":"Un Autre","username":"autre","password":"motdepasse-long"}""")
                        .with(from("127.0.0.1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_DONE"));
    }

    private static MockHttpServletRequestBuilder creation(String body) {
        return post("/api/setup/admin").contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }
}
