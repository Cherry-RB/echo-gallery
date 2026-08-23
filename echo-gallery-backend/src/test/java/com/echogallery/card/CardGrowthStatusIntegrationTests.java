package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;
import com.echogallery.work.WorkCardRepository;
import com.echogallery.work.WorkRepository;

import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class CardGrowthStatusIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkCardRepository workCardRepository;

    @Autowired
    private WorkRepository workRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        workCardRepository.deleteAll();
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void ownerCanRoundTripEveryGrowthStatusWithoutChangingReviewFields() throws Exception {
        String token = register("growth-owner", "growth-owner@example.com");
        long cardId = createCard(token, "成長狀態卡片");
        Card before = cardRepository.findById(cardId).orElseThrow();
        ZonedDateTime nextShowAt = before.getNextShowAt();
        ZonedDateTime lastOpenAt = before.getLastOpenAt();
        ZonedDateTime lastInteractionAt = before.getLastInteractionAt();
        int openCount = before.getOpenCount();

        for (CardGrowthStatus growthStatus : CardGrowthStatus.values()) {
            mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new GrowthStatusRequest(growthStatus.name()))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.growthStatus").value(growthStatus.name()));
        }

        Card after = cardRepository.findById(cardId).orElseThrow();
        assertThat(after.getGrowthStatus()).isEqualTo(CardGrowthStatus.MATURE);
        assertThat(after.getNextShowAt()).isEqualTo(nextShowAt);
        assertThat(after.getOpenCount()).isEqualTo(openCount);
        assertThat(after.getLastOpenAt()).isEqualTo(lastOpenAt);
        assertThat(after.getLastInteractionAt()).isEqualTo(lastInteractionAt);
    }

    @Test
    void rejectsMissingNullAndUnknownGrowthStatusWithoutChangingCard() throws Exception {
        String token = register("growth-validation", "growth-validation@example.com");
        long cardId = createCard(token, "驗證成長狀態");

        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"growthStatus\":null}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"growthStatus\":\"COMPLETED\"}"))
                .andExpect(status().isBadRequest());

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.UNMARKED);
    }

    @Test
    void requiresAuthenticationAndRejectsAnotherUser() throws Exception {
        String ownerToken = register("growth-card-owner", "growth-card-owner@example.com");
        String otherToken = register("growth-card-other", "growth-card-other@example.com");
        long cardId = createCard(ownerToken, "私人成長狀態");
        String requestBody = objectMapper.writeValueAsString(new GrowthStatusRequest("SEED"));

        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/cards/{id}/growth-status", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.UNMARKED);
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest(
                                username,
                                email,
                                "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createCard(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardRequest(
                                "note",
                                title,
                                List.of(),
                                10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.growthStatus").value("UNMARKED"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record CardRequest(String type, String title, List<String> tags, Integer intervalDays) {}

    private record GrowthStatusRequest(String growthStatus) {}
}
