package com.echogallery.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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

import com.echogallery.card.Card;
import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.User;
import com.echogallery.user.UserRepository;
import com.echogallery.work.WorkCardRepository;
import com.echogallery.work.WorkRepository;

import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import(DemoSessionIntegrationTests.ClockTestConfig.class)
class DemoSessionIntegrationTests extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CardRepository cardRepository;
    @Autowired TagRepository tagRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkCardRepository workCardRepository;
    @Autowired WorkRepository workRepository;
    @Autowired DemoSessionService demoSessionService;
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
    void createsIsolatedLibrariesWithTwoFiveCardTodayBatches() throws Exception {
        DemoSession tech = start("tech");
        DemoSession visual = start("visual");
        DemoSession writing = start("writing");

        assertLibrary(tech.userId(), "tech", 15, 10);
        assertLibrary(visual.userId(), "visual", 15, 10);
        assertLibrary(writing.userId(), "writing", 15, 10);
        assertThat(cardRepository.findAllByUserId(tech.userId()))
                .extracting(Card::getTitle)
                .doesNotContainAnyElementsOf(cardRepository.findAllByUserId(visual.userId()).stream().map(Card::getTitle).toList());

        MvcResult techFirst = today(tech.token()).andExpect(jsonPath("$.cards.length()").value(5)).andReturn();
        MvcResult visualFirst = today(visual.token()).andExpect(jsonPath("$.cards.length()").value(5)).andReturn();
        MvcResult writingFirst = today(writing.token()).andExpect(jsonPath("$.cards.length()").value(5)).andReturn();
        next(tech.token(), batchTime(techFirst)).andExpect(jsonPath("$.cards.length()").value(5));
        next(visual.token(), batchTime(visualFirst)).andExpect(jsonPath("$.cards.length()").value(5));
        next(writing.token(), batchTime(writingFirst)).andExpect(jsonPath("$.cards.length()").value(5));
    }

    @Test
    void resetDeletesChangesAndRestoresTheInitialCatalog() throws Exception {
        DemoSession session = start("writing");
        Card changed = cardRepository.findAllByUserId(session.userId()).getFirst();
        changed.setTitle("使用者已修改的資料");
        cardRepository.saveAndFlush(changed);

        String resetToken = reset(session.token());
        assertLibrary(session.userId(), "writing", 15, 10);
        assertThat(cardRepository.findAllByUserId(session.userId()))
                .extracting(Card::getTitle).doesNotContain("使用者已修改的資料");
        today(resetToken).andExpect(jsonPath("$.cards.length()").value(5));
    }

    @Test
    void expiredSessionsAreRejectedAndCleanupRemovesOnlyExpiredDemoData() throws Exception {
        DemoSession expired = start("tech");
        DemoSession active = start("visual");
        User expiredUser = userRepository.findById(expired.userId()).orElseThrow();
        expiredUser.setDemoExpiresAt(ZonedDateTime.now(clock).minusMinutes(1));
        userRepository.saveAndFlush(expiredUser);

        mockMvc.perform(post("/api/auth/demo-sessions/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(expired.token())))
                .andExpect(status().isUnauthorized());

        demoSessionService.clearExpiredSessions();
        assertThat(userRepository.findById(expired.userId())).isEmpty();
        assertThat(cardRepository.findAllByUserId(expired.userId())).isEmpty();
        assertThat(userRepository.findById(active.userId())).isPresent();
        assertThat(cardRepository.findAllByUserId(active.userId())).hasSize(15);
    }

    private void assertLibrary(long userId, String library, int expectedCards, int expectedToday) {
        User user = userRepository.findById(userId).orElseThrow();
        assertThat(user.getDemoLibrary()).isEqualTo(library);
        assertThat(user.getDemoExpiresAt()).isEqualTo(ZonedDateTime.now(clock).plusHours(24));
        assertThat(cardRepository.findAllByUserId(userId)).hasSize(expectedCards);
        assertThat(cardRepository.findAllByUserId(userId)).filteredOn(card -> !card.isArchived() && card.getNextShowAt() != null
                && !card.getNextShowAt().toLocalDate().isAfter(ZonedDateTime.now(clock).toLocalDate())).hasSize(expectedToday);
        assertThat(workRepository.findAllByUserId(userId)).hasSize(1);
    }

    private DemoSession start(String library) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo-sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"" + library + "\"}"))
                .andExpect(status().isOk()).andReturn();
        var response = objectMapper.readTree(result.getResponse().getContentAsString());
        return new DemoSession(response.get("id").asLong(), response.get("token").asText());
    }

    private String reset(String token) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo-sessions/reset")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private org.springframework.test.web.servlet.ResultActions today(String token) throws Exception {
        return mockMvc.perform(post("/api/cards/today/prepare").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions next(String token, String batchOfferedAt) throws Exception {
        return mockMvc.perform(post("/api/cards/today/next")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentBatchOfferedAt\":\"" + batchOfferedAt + "\"}"))
                .andExpect(status().isOk());
    }

    private String batchTime(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("batchOfferedAt").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private record DemoSession(long userId, String token) {}

    @TestConfiguration
    static class ClockTestConfig {
        @Bean @Primary MutableClock demoClock() {
            return new MutableClock(Instant.parse("2026-08-24T04:00:00Z"), ZoneId.of("Asia/Taipei"));
        }
    }

    static class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;
        MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        void set(Instant instant) { this.instant = instant; }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(instant, zone); }
        @Override public Instant instant() { return instant; }
    }
}
