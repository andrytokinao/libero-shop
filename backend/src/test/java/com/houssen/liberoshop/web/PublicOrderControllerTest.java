package com.houssen.liberoshop.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Ordering from a table is for the shop's Wi-Fi: from the Internet, not even the menu answers. */
@SpringBootTest
@AutoConfigureMockMvc
class PublicOrderControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("a caller from the Internet is turned away before the table is even looked at")
    void internetCallerRefused() throws Exception {
        mvc.perform(get("/api/public/tables/any-token/menu").with(request -> {
                    request.setRemoteAddr("203.0.113.7");
                    return request;
                }))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_NETWORK_ONLY"));

        mvc.perform(post("/api/public/tables/any-token/orders").with(request -> {
                    request.setRemoteAddr("127.0.0.1");
                    request.addHeader("X-Forwarded-For", "203.0.113.7");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"lines\":[{\"productId\":1,\"quantity\":1}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a phone on the Wi-Fi gets through to the table check")
    void localCallerReachesTheTable() throws Exception {
        mvc.perform(get("/api/public/tables/any-token/menu").with(request -> {
                    request.setRemoteAddr("192.168.1.42");
                    return request;
                }))
                // Past the network guard: refused now for the unknown code, or the switch being off.
                .andExpect(status().isNotFound());
    }
}
