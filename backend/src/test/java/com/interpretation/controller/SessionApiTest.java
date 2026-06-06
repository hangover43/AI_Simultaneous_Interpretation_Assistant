package com.interpretation.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    @Test
    void createSessionReturnsDefaultsAndGlossary() throws Exception {
        mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "AI 技术分享",
                                  "sourceLanguage": "auto",
                                  "targetLanguage": "zh-CN",
                                  "revisionWindowSize": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId", notNullValue()))
                .andExpect(jsonPath("$.topic").value("AI 技术分享"))
                .andExpect(jsonPath("$.sourceLanguage").value("auto"))
                .andExpect(jsonPath("$.targetLanguage").value("zh-CN"))
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.glossary", hasSize(greaterThan(0))));
    }

    @Test
    void stopSessionChangesStatus() throws Exception {
        String response = mockMvc.perform(post("/api/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "topic": "通用会议",
                                  "sourceLanguage": "auto",
                                  "targetLanguage": "zh-CN",
                                  "revisionWindowSize": 8
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sessionId = response.split("\"sessionId\":\"")[1].split("\"")[0];

        mockMvc.perform(post("/api/sessions/{sessionId}/stop", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.status").value("stopped"));
    }

    @Test
    void clearSegmentsReturnsNotFoundForMissingSession() throws Exception {
        mockMvc.perform(delete("/api/sessions/missing-session/segments"))
                .andExpect(status().isNotFound());
    }
}
