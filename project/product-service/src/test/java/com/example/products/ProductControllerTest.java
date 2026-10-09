package com.example.products;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.matchesPattern;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void createsAndReadsPersistedProduct() throws Exception {
        String location = mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Keyboard\",\"price\":19.99}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/products/[0-9]+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Keyboard"))
                .andExpect(jsonPath("$.price").value(19.99));
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Keyboard"));
    }

    @Test
    void emptyCatalogReturnsEmptyArray() throws Exception {
        mvc.perform(get("/products")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "9999999999.99"})
    void acceptsPriceBoundaries(String price) throws Exception {
        String location = mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Book\",\"price\":" + price + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(Double.parseDouble(price)));
    }

    @Test
    void rejectsOversizedName() throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(256) + "\",\"price\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingProductReturns404() throws Exception {
        mvc.perform(get("/products/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/products/invalid")).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"name\":\" \",\"price\":1}", "{\"name\":\"Book\"}",
            "{\"name\":\"Book\",\"price\":-1}", "{\"name\":\"Book\",\"price\":1.001}",
            "{\"name\":\"Book\",\"price\":10000000000}", "{", "null"
    })
    void rejectsInvalidProduct(String body) throws Exception {
        mvc.perform(post("/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
