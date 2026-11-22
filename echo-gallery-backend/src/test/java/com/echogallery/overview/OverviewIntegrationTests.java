package com.echogallery.overview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.echogallery.support.IntegrationTestBase;

import tools.jackson.databind.ObjectMapper;

/** 首頁摘要只讀取探索紀錄及其卡片參考，不再依賴探索線。 */
@AutoConfigureMockMvc
class OverviewIntegrationTests extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void readsLatestRecordAndCardReferenceWithoutUsingLegacyThreads() throws Exception {
        String token = register("overview-record-owner");
        long experimentId = createExperiment(token);
        long cardId = createCard(token);
        jdbc.update("update cards set created_at = ? where id = ?", OffsetDateTime.now().minusDays(60), cardId);
        long recordId = createRecord(token, experimentId);

        mvc.perform(post("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CARD\",\"cardId\":%d}".formatted(cardId)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/overview/current").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeExperimentCount").value(1))
                .andExpect(jsonPath("$.experimentTries[0].experimentId").value(experimentId))
                .andExpect(jsonPath("$.experimentTries[0].summary").value("新的觀察"))
                .andExpect(jsonPath("$.experimentTries[0].explorationRecordCount").value(1));
        mvc.perform(get("/api/overview/recent").param("periodDays", "30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.flow.reengagedCardCount").value(1))
                .andExpect(jsonPath("$.period.recentExperimentMaterials[0].cardId").value(cardId))
                .andExpect(jsonPath("$.period.recentExperimentMaterials[0].experimentId").value(experimentId));
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Registration(username, username + "@example.com", "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }

    private long createExperiment(String token) throws Exception {
        String body = mvc.perform(post("/api/experiments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"首頁紀錄實驗\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createCard(String token) throws Exception {
        String body = mvc.perform(post("/api/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"note\",\"title\":\"首頁參考卡\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createRecord(String token, long experimentId) throws Exception {
        String body = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"新的觀察\",\"recordedOn\":\"2026-11-22\",\"understandingNode\":false,\"methodCandidate\":false,\"highlight\":\"GENERAL\",\"requestId\":\"%s\"}"
                                .formatted(UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Registration(String username, String email, String password) {}
}
