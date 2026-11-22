package com.echogallery.experiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.UserRepository;

import tools.jackson.databind.ObjectMapper;

/** 驗證退場路徑不會重新觸碰舊探索模型。 */
@AutoConfigureMockMvc
class ExplorationRetirementIntegrationTests extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExperimentRepository experiments;
    @Autowired UserRepository users;
    private Long experimentId;
    private Long ownerId;

    @AfterEach
    void clean() {
        if (experimentId != null) {
            experiments.deleteById(experimentId);
            experiments.flush();
        }
        if (ownerId != null) users.deleteById(ownerId);
    }

    @Test
    void legacyExplorationEndpointsReturnGone() throws Exception {
        String token = register("retired-exploration-owner");
        experimentId = createExperiment(token);

        mvc.perform(get("/api/experiments/{experimentId}/exploration", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone());
        mvc.perform(get("/api/experiments/{experimentId}/activity", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone());
        mvc.perform(post("/api/experiments/{experimentId}/activity/organizations/preview", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isGone());
        mvc.perform(get("/api/experiments/{experimentId}/threads", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone());
        mvc.perform(get("/api/cards/{cardId}/exploration-threads", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone());
        mvc.perform(get("/api/creative-works/{workId}/exploration-threads", 999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isGone());
    }

    private long createExperiment(String token) throws Exception {
        String body = mvc.perform(post("/api/experiments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"退場路徑測試\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String register(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new Registration(username, username + "@example.com", "password123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var response = json.readTree(body);
        ownerId = response.get("id").asLong();
        return response.get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record Registration(String username, String email, String password) {}
}
