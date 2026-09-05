package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
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
class CardRecurrenceIntegrationTests extends IntegrationTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CardRepository cardRepository;
    @Autowired TagRepository tagRepository;
    @Autowired UserRepository userRepository;
    @Autowired WorkCardRepository workCardRepository;
    @Autowired WorkRepository workRepository;
    @Autowired TodayCardIntegrationTests.MutableClock clock;

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
    void pauseClearsRecurrenceWithoutChangingInteractionStateAndIsIdempotent() throws Exception {
        String token = register("pause-owner", "pause-owner@example.com");
        long cardId = createCard(token, "暫停測試", 10);
        Card before = cardRepository.findById(cardId).orElseThrow();
        before.setOpenCount(3);
        before.setSnoozeCount(4);
        before.setGrowthStatus(CardGrowthStatus.GROWING);
        cardRepository.saveAndFlush(before);

        pause(token, cardId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(nullValue()))
                .andExpect(jsonPath("$.nextShowAt").value(nullValue()))
                .andExpect(jsonPath("$.openCount").value(3))
                .andExpect(jsonPath("$.snoozeCount").value(4))
                .andExpect(jsonPath("$.growthStatus").value("GROWING"));

        pause(token, cardId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(nullValue()))
                .andExpect(jsonPath("$.nextShowAt").value(nullValue()));

        Card paused = cardRepository.findById(cardId).orElseThrow();
        assertThat(paused.getIntervalDays()).isNull();
        assertThat(paused.getNextShowAt()).isNull();
        assertThat(paused.getOpenCount()).isEqualTo(3);
        assertThat(paused.getSnoozeCount()).isEqualTo(4);
        assertThat(paused.getGrowthStatus()).isEqualTo(CardGrowthStatus.GROWING);
    }

    @Test
    void pausedCardDoesNotEnterTodayButRemainsInAllCards() throws Exception {
        String token = register("pause-list-owner", "pause-list-owner@example.com");
        long cardId = createCard(token, "列表測試", 10);

        pause(token, cardId).andExpect(status().isOk());

        mockMvc.perform(post("/api/cards/today/prepare")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards").isEmpty());

        mockMvc.perform(post("/api/cards/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"boardType\":\"all\",\"pageNumber\":1,\"pageSize\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cardId));
    }

    @Test
    void resumeSchedulesFromTaipeiStartOfDayAndSameRequestIsIdempotent() throws Exception {
        String token = register("resume-owner", "resume-owner@example.com");
        long cardId = createCard(token, "恢復測試", 10);
        pause(token, cardId).andExpect(status().isOk());

        resume(token, cardId, "{\"intervalDays\":10}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(10))
                .andExpect(jsonPath("$.nextShowAt").value("2026-09-03T00:00:00+08:00"));

        ZonedDateTime firstSchedule = cardRepository.findById(cardId).orElseThrow().getNextShowAt();
        clock.set(Instant.parse("2026-08-25T04:00:00Z"));

        MvcResult repeatedResume = resume(token, cardId, "{\"intervalDays\":10}")
                .andExpect(status().isOk())
                .andReturn();

        ZonedDateTime repeatedSchedule = ZonedDateTime.parse(objectMapper
                .readTree(repeatedResume.getResponse().getContentAsString())
                .get("nextShowAt")
                .asText());
        assertThat(repeatedSchedule.toInstant()).isEqualTo(firstSchedule.toInstant());

        assertThat(cardRepository.findById(cardId).orElseThrow().getNextShowAt())
                .isEqualTo(firstSchedule);
    }

    @Test
    void resumeRejectsMissingNullAndOutOfRangeIntervals() throws Exception {
        String token = register("resume-validation", "resume-validation@example.com");
        long cardId = createCard(token, "恢復驗證", 10);
        pause(token, cardId).andExpect(status().isOk());

        for (String body : List.of("{}", "{\"intervalDays\":null}", "{\"intervalDays\":0}", "{\"intervalDays\":366}")) {
            resume(token, cardId, body).andExpect(status().isBadRequest());
        }

        Card paused = cardRepository.findById(cardId).orElseThrow();
        assertThat(paused.getIntervalDays()).isNull();
        assertThat(paused.getNextShowAt()).isNull();
    }

    @Test
    void updateRecurrenceChangesActiveIntervalAndReschedulesFromToday() throws Exception {
        String token = register("update-recurrence", "update-recurrence@example.com");
        long cardId = createCard(token, "調整週期", 10);

        updateRecurrence(token, cardId, "{\"intervalDays\":40}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(40))
                .andExpect(jsonPath("$.nextShowAt").value("2026-10-03T00:00:00+08:00"));

        Card updated = cardRepository.findById(cardId).orElseThrow();
        assertThat(updated.getIntervalDays()).isEqualTo(40);
        assertThat(updated.getNextShowAt()).isEqualTo(ZonedDateTime.parse("2026-10-03T00:00:00+08:00"));
    }

    @Test
    void updateRecurrenceRejectsInvalidIntervalsArchivedCardsAndOtherUsers() throws Exception {
        String ownerToken = register("update-owner", "update-owner@example.com");
        String otherToken = register("update-other", "update-other@example.com");
        long cardId = createCard(ownerToken, "週期限制", 10);

        for (String body : List.of("{}", "{\"intervalDays\":0}", "{\"intervalDays\":366}")) {
            updateRecurrence(ownerToken, cardId, body).andExpect(status().isBadRequest());
        }
        updateRecurrence(otherToken, cardId, "{\"intervalDays\":20}")
                .andExpect(status().isForbidden());

        archive(ownerToken, cardId, true).andExpect(status().isOk());
        updateRecurrence(ownerToken, cardId, "{\"intervalDays\":20}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void pauseAndResumeRejectAnotherUsersCard() throws Exception {
        String ownerToken = register("recurrence-owner", "recurrence-owner@example.com");
        String otherToken = register("recurrence-other", "recurrence-other@example.com");
        long cardId = createCard(ownerToken, "權限測試", 10);

        pause(otherToken, cardId).andExpect(status().isForbidden());
        resume(otherToken, cardId, "{\"intervalDays\":20}").andExpect(status().isForbidden());

        Card unchanged = cardRepository.findById(cardId).orElseThrow();
        assertThat(unchanged.getIntervalDays()).isEqualTo(10);
        assertThat(unchanged.getNextShowAt()).isEqualTo(ZonedDateTime.parse("2026-09-03T00:00:00+08:00"));
    }

    @Test
    void pausedCardCannotBeSnoozedOrRescheduledByRead() throws Exception {
        String token = register("paused-actions", "paused-actions@example.com");
        long cardId = createCard(token, "暫停互動測試", 10);
        pause(token, cardId).andExpect(status().isOk());

        mockMvc.perform(put("/api/cards/{id}/snooze", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nextIntervalDays\":5}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/cards/{id}/read", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalDays").value(nullValue()))
                .andExpect(jsonPath("$.nextShowAt").value(nullValue()));
    }

    @Test
    void unarchiveRestoresOriginalRecurrenceScheduleWithoutRecalculating() throws Exception {
        String token = register("archive-schedule", "archive-schedule@example.com");
        long cardId = createCard(token, "封存排程測試", 10);
        ZonedDateTime originalSchedule = cardRepository.findById(cardId).orElseThrow().getNextShowAt();

        archive(token, cardId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true))
                .andExpect(jsonPath("$.intervalDays").value(10));

        clock.set(Instant.parse("2026-09-04T04:00:00Z"));
        MvcResult unarchiveResult = archive(token, cardId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(false))
                .andExpect(jsonPath("$.intervalDays").value(10))
                .andReturn();

        ZonedDateTime restoredSchedule = ZonedDateTime.parse(objectMapper
                .readTree(unarchiveResult.getResponse().getContentAsString())
                .get("nextShowAt")
                .asText());
        assertThat(restoredSchedule.toInstant()).isEqualTo(originalSchedule.toInstant());
        assertThat(cardRepository.findById(cardId).orElseThrow().getNextShowAt())
                .isEqualTo(originalSchedule);

        mockMvc.perform(post("/api/cards/today/prepare")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].id").value(cardId));
    }

    @Test
    void unarchiveKeepsPreviouslyPausedCardPaused() throws Exception {
        String token = register("archive-paused", "archive-paused@example.com");
        long cardId = createCard(token, "封存暫停測試", 10);
        pause(token, cardId).andExpect(status().isOk());

        archive(token, cardId, true).andExpect(status().isOk());
        archive(token, cardId, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(false))
                .andExpect(jsonPath("$.intervalDays").value(nullValue()))
                .andExpect(jsonPath("$.nextShowAt").value(nullValue()));

        Card restored = cardRepository.findById(cardId).orElseThrow();
        assertThat(restored.getIntervalDays()).isNull();
        assertThat(restored.getNextShowAt()).isNull();
    }

    private org.springframework.test.web.servlet.ResultActions pause(String token, long cardId) throws Exception {
        return mockMvc.perform(put("/api/cards/{id}/pause", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private org.springframework.test.web.servlet.ResultActions resume(String token, long cardId, String body) throws Exception {
        return mockMvc.perform(put("/api/cards/{id}/resume", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions updateRecurrence(
            String token,
            long cardId,
            String body) throws Exception {
        return mockMvc.perform(put("/api/cards/{id}/recurrence", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions archive(
            String token,
            long cardId,
            boolean archivedStatus) throws Exception {
        return mockMvc.perform(put("/api/cards/{id}/archive", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archivedStatus\":" + archivedStatus + "}"));
    }

    private long createCard(String token, String title, Integer intervalDays) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CardRequest("note", title, List.of(), intervalDays))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegistrationRequest(username, email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}
    private record CardRequest(String type, String title, List<String> tags, Integer intervalDays) {}
}
