package com.echogallery.overview;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echogallery.support.IntegrationTestBase;

import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class OverviewIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsCurrentAndPeriodObservationForAuthenticatedUser() throws Exception {
        String token = register("overview-owner", "overview-owner@example.com");

        mockMvc.perform(get("/api/overview")
                        .param("periodDays", "30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodDays").value(30))
                .andExpect(jsonPath("$.current.recurringCardCount").isNumber())
                .andExpect(jsonPath("$.period.flow.reengagedCardCount").isNumber())
                .andExpect(jsonPath("$.period.activities.length()").value(7))
                .andExpect(jsonPath("$.period.recentExperimentMaterials").isArray());

        mockMvc.perform(get("/api/overview/card-return")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state.recurringCardCount").isNumber())
                .andExpect(jsonPath("$.cadenceBands.length()").value(7))
                .andExpect(jsonPath("$.forecastDays.length()").value(7))
                .andExpect(jsonPath("$.snoozeBands.length()").value(3));
    }

    @Test
    void rejectsUnsupportedObservationPeriod() throws Exception {
        String token = register("overview-period", "overview-period@example.com");

        mockMvc.perform(get("/api/overview")
                        .param("periodDays", "14")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest());
    }

    private String register(String username, String email) throws Exception {
        String body = objectMapper.writeValueAsString(new RegistrationRequest(username, email, "password123"));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {
    }
}
