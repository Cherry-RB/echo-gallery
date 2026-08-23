package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(TodayCardIntegrationTests.ClockTestConfig.class)
class TodayCardIntegrationTests extends IntegrationTestBase {

    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CardRepository cardRepository;
    @Autowired TagRepository tagRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkCardRepository workCardRepository;
    @Autowired WorkRepository workRepository;
    @Autowired MutableClock clock;

    @BeforeEach
    void cleanDatabase() {
        workCardRepository.deleteAll();
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
        clock.set(Instant.parse("2026-08-24T04:00:00Z"));
    }

    @Test
    void prepareCreatesAtMostFiveCardsAndRestoresWithoutRefill() throws Exception {
        String token = register("today-owner", "today-owner@example.com");
        for (int i = 0; i < 7; i++) {
            makeDue(createCard(token, "card-" + i));
        }

        MvcResult first = prepare(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(5))
                .andReturn();
        String batch = objectMapper.readTree(first.getResponse().getContentAsString())
                .get("batchOfferedAt").asText();

        List<Card> offered = cardRepository.findAll().stream()
                .filter(card -> card.getLastOfferedAt() != null)
                .toList();
        assertThat(offered).hasSize(5);
        assertThat(offered).extracting(card -> card.getLastOfferedAt().toInstant())
                .containsOnly(ZonedDateTime.parse(batch).toInstant());
        Card reviewed = offered.getFirst();
        reviewed.setNextShowAt(ZonedDateTime.now(clock).plusDays(10));
        cardRepository.saveAndFlush(reviewed);

        MvcResult restored = prepare(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(4))
                .andReturn();
        assertThat(ZonedDateTime.parse(batchTime(restored)).toInstant())
                .isEqualTo(ZonedDateTime.parse(batch).toInstant());
        assertThat(cardRepository.findAll().stream().filter(card -> card.getLastOfferedAt() != null)).hasSize(5);
    }

    @Test
    void nextCreatesDistinctBatchAndRejectsStaleRequest() throws Exception {
        String token = register("next-owner", "next-owner@example.com");
        for (int i = 0; i < 8; i++) {
            makeDue(createCard(token, "next-" + i));
        }
        String firstBatch = batchTime(prepare(token).andReturn());

        MvcResult next = mockMvc.perform(post("/api/cards/today/next")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBatchOfferedAt\":\"" + firstBatch + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(3))
                .andReturn();
        String secondBatch = batchTime(next);
        assertThat(ZonedDateTime.parse(secondBatch).toInstant())
                .isAfter(ZonedDateTime.parse(firstBatch).toInstant());

        mockMvc.perform(post("/api/cards/today/next")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBatchOfferedAt\":\"" + firstBatch + "\"}"))
                .andExpect(status().isConflict());
        assertThat(cardRepository.findAll().stream().filter(card -> card.getLastOfferedAt() != null)).hasSize(8);
    }

    @Test
    void repeatedReadForSameOfferOnlyCountsOnce() throws Exception {
        String token = register("read-owner", "read-owner@example.com");
        long cardId = createCard(token, "read-card");
        makeDue(cardId);
        prepare(token).andExpect(status().isOk());

        mockMvc.perform(put("/api/cards/{id}/read", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1));
        mockMvc.perform(put("/api/cards/{id}/read", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1));
        assertThat(cardRepository.findById(cardId).orElseThrow().getOpenCount()).isEqualTo(1);
    }

    @Test
    void oldOfferBecomesEligibleAgainAfterTaipeiMidnight() throws Exception {
        String token = register("midnight-owner", "midnight-owner@example.com");
        long cardId = createCard(token, "midnight-card");
        makeDue(cardId);
        clock.set(Instant.parse("2026-08-24T15:59:00Z"));
        String firstBatch = batchTime(prepare(token).andReturn());

        clock.set(Instant.parse("2026-08-24T16:00:00Z"));
        MvcResult nextDay = prepare(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andReturn();
        assertThat(ZonedDateTime.parse(batchTime(nextDay)).toInstant())
                .isAfter(ZonedDateTime.parse(firstBatch).toInstant());
    }

    @Test
    void emptyPrepareDoesNotCreateBatch() throws Exception {
        String token = register("empty-owner", "empty-owner@example.com");

        prepare(token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(0))
                .andExpect(jsonPath("$.batchOfferedAt").doesNotExist());
        assertThat(cardRepository.findAll()).isEmpty();
    }

    @Test
    void exhaustedNextKeepsCurrentBatchAndChangesNothing() throws Exception {
        String token = register("exhausted-owner", "exhausted-owner@example.com");
        long cardId = createCard(token, "only-card");
        makeDue(cardId);
        Card beforeOffer = cardRepository.findById(cardId).orElseThrow();
        ZonedDateTime originalNextShowAt = beforeOffer.getNextShowAt();
        String batch = batchTime(prepare(token).andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/cards/today/next")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBatchOfferedAt\":\"" + batch + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(0));

        Card after = cardRepository.findById(cardId).orElseThrow();
        assertThat(after.getLastOfferedAt().toInstant()).isEqualTo(ZonedDateTime.parse(batch).toInstant());
        assertThat(after.getNextShowAt()).isEqualTo(originalNextShowAt);
        assertThat(after.getOpenCount()).isZero();
        assertThat(after.getLastOpenAt()).isNull();
        assertThat(after.getLastInteractionAt()).isNull();
    }

    @Test
    void prepareOnlyOffersCurrentUsersCards() throws Exception {
        String ownerToken = register("offer-owner", "offer-owner@example.com");
        String otherToken = register("offer-other", "offer-other@example.com");
        long ownerCardId = createCard(ownerToken, "owner-card");
        long otherCardId = createCard(otherToken, "other-card");
        makeDue(ownerCardId);
        makeDue(otherCardId);

        prepare(ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards.length()").value(1))
                .andExpect(jsonPath("$.cards[0].id").value(ownerCardId));
        assertThat(cardRepository.findById(otherCardId).orElseThrow().getLastOfferedAt()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions prepare(String token) throws Exception {
        return mockMvc.perform(post("/api/cards/today/prepare")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private String batchTime(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("batchOfferedAt").asText();
    }

    private void makeDue(long cardId) {
        Card card = cardRepository.findById(cardId).orElseThrow();
        card.setNextShowAt(ZonedDateTime.now(clock).minusDays(1));
        cardRepository.saveAndFlush(card);
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegistrationRequest(username, email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createCard(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardRequest("note", title, List.of(), 10))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}
    private record CardRequest(String type, String title, List<String> tags, Integer intervalDays) {}

    @TestConfiguration
    static class ClockTestConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-24T04:00:00Z"), TAIPEI);
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
