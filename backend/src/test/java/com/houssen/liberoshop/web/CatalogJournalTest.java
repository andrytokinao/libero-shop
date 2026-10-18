package com.houssen.liberoshop.web;

import com.houssen.liberoshop.entity.Product;
import com.houssen.liberoshop.repository.ProductRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.UUID;

import static com.houssen.liberoshop.util.QuantityAssertions.qty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who changed a price, what it was, and when: through the real screens' calls. */
@SpringBootTest
@AutoConfigureMockMvc
class CatalogJournalTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JsonMapper json;
    @Autowired
    private ProductRepository products;

    private String nadia() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nadia\",\"password\":\"vaha2026\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asString();
    }

    private void setPrice(String token, Long productId, String price) throws Exception {
        mvc.perform(put("/api/products/" + productId + "/sale-price").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"price\":" + price + "}"));
    }

    @Test
    @DisplayName("un prix change est note : qui, avant, apres ; un prix remis tel quel ou refuse n'ajoute rien")
    void changementDePrixNote() throws Exception {
        Product riz = products.save(Product.builder().name("Riz " + UUID.randomUUID()).price(new BigDecimal("12500"))
                .stockQuantity(qty(5)).build());
        String nadia = nadia();

        setPrice(nadia, riz.getId(), "13500");
        setPrice(nadia, riz.getId(), "13500");
        setPrice(nadia, riz.getId(), "0");

        mvc.perform(get("/api/products/" + riz.getId() + "/changes").header("Authorization", nadia))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].what").value("Prix de vente"))
                .andExpect(jsonPath("$[0].before").value("12500"))
                .andExpect(jsonPath("$[0].after").value("13500"))
                .andExpect(jsonPath("$[0].changedBy").value("Nadia Rasolofo"));
    }
}
