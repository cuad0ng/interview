package com.example.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountRepository accounts;

    private org.springframework.test.web.servlet.ResultActions register(String username, String password) throws Exception {
        var csrf = mvc.perform(get("/auth/csrf")).andExpect(status().isOk()).andReturn();
        var token = json.readTree(csrf.getResponse().getContentAsString());
        return mvc.perform(post("/auth/register")
                .session((MockHttpSession) csrf.getRequest().getSession())
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("username", username, "password", password))));
    }

    private String basic(String username, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void registersHashesAndAuthenticatesWithoutExposingCredentials() throws Exception {
        register("alice", "correct-password")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        String hash = accounts.findByUsername("alice").orElseThrow().getPasswordHash();
        assertNotEquals("correct-password", hash);
        assertTrue(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().matches("correct-password", hash));
        mvc.perform(get("/auth/me").header("Authorization", basic("alice", "correct-password")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/auth/me").header("Authorization", basic("alice", "wrong-password")))
                .andExpect(status().isUnauthorized());
        register("alice", "different-password").andExpect(status().isConflict());
    }

    @Test
    void rejectsMissingAndUnknownCredentials() throws Exception {
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/auth/me").header("Authorization", basic("unknown", "correct-password")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesInputAndRequiresCsrfProtection() throws Exception {
        register("a", "correct-password").andExpect(status().isBadRequest());
        register("invalid name", "correct-password").andExpect(status().isBadRequest());
        register("valid", "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid username or password"));
        register("valid", "x".repeat(73)).andExpect(status().isBadRequest());
        register("valid", "界".repeat(25)).andExpect(status().isBadRequest());
        mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"valid\",\"password\":\"correct-password\"}"))
                .andExpect(status().isForbidden());
        assertTrue(accounts.findByUsername("valid").isEmpty());
    }
}
