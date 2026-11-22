package com.echogallery.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.echogallery.card.CardRepository;
import com.echogallery.experiment.ExperimentPinnedPracticeRepository;
import com.echogallery.experiment.ExperimentRecordRepository;
import com.echogallery.experiment.ExperimentTopicRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.UserRepository;
import com.echogallery.work.CreativeWorkRepository;
import com.echogallery.work.WorkSourceRepository;

import tools.jackson.databind.ObjectMapper;

/** Demo 資料必須能建立、讀取並在重設或逾期時完整清理新紀錄關聯。 */
@AutoConfigureMockMvc
class DemoSessionIntegrationTests extends IntegrationTestBase {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DemoSessionService demos;
    @Autowired UserRepository users;
    @Autowired CardRepository cards;
    @Autowired CreativeWorkRepository works;
    @Autowired WorkSourceRepository workSources;
    @Autowired ExperimentRecordRepository records;
    @Autowired ExperimentTopicRepository topics;
    @Autowired ExperimentPinnedPracticeRepository pinnedPractices;

    @Test
    void createsIsolatedDemoLibrariesAndResetsTheirCatalog() throws Exception {
        DemoSession first = start("tech");
        DemoSession second = start("visual");

        assertThat(cards.findAllByUserId(first.userId())).hasSize(15);
        assertThat(cards.findAllByUserId(second.userId())).hasSize(15);
        assertThat(works.countByUserId(first.userId())).isEqualTo(1);

        var changed = cards.findAllByUserId(first.userId()).getFirst();
        changed.setTitle("重設前的變更");
        cards.saveAndFlush(changed);
        reset(first.token());

        assertThat(cards.findAllByUserId(first.userId())).extracting(card -> card.getTitle())
                .doesNotContain("重設前的變更");
        assertThat(cards.findAllByUserId(second.userId())).hasSize(15);
        assertThat(works.countByUserId(first.userId())).isEqualTo(1);
    }

    @Test
    void resetClearsNewRecordGraphAndRecordWorkSourcesBeforeDeletingExperiments() throws Exception {
        DemoSession session = start("writing");
        long experimentId = createExperiment(session.token(), "新紀錄清理測試");
        long recordId = createRecord(session.token(), experimentId, "清理前紀錄");
        long topicId = createTopic(session.token(), experimentId, "整理主題");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                        "/api/experiments/{experimentId}/records/{recordId}/topics", experimentId, recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"topicIds\":[%d]}".formatted(topicId)))
                .andExpect(status().isOk());
        String practiceBody = mvc.perform(post("/api/experiments/{experimentId}/pinned-practices", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"先寫一段觀察\",\"slot\":1}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long practiceId = json.readTree(practiceBody).get("id").asLong();

        long workId = works.findAll().stream().filter(work -> work.getUser().getId().equals(session.userId()))
                .findFirst().orElseThrow().getId();
        mvc.perform(post("/api/creative-works/{workId}/sources", workId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sources\":[{\"recordId\":%d}]}".formatted(recordId)))
                .andExpect(status().isOk());
        assertThat(workSources.existsByRecordId(recordId)).isTrue();

        reset(session.token());

        assertThat(records.findById(recordId)).isEmpty();
        assertThat(topics.findById(topicId)).isEmpty();
        assertThat(pinnedPractices.findById(practiceId)).isEmpty();
        assertThat(workSources.existsByRecordId(recordId)).isFalse();
        assertThat(works.countByUserId(session.userId())).isEqualTo(1);
    }

    @Test
    void resetClearsCardReferencesBeforeRemovingTheRecord() throws Exception {
        DemoSession session = start("tech");
        long experimentId = createExperiment(session.token(), "Reference cleanup");
        long recordId = createRecord(session.token(), experimentId, "Reference record");
        long cardId = cards.findAllByUserId(session.userId()).getFirst().getId();

        mvc.perform(post("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(session.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"CARD\",\"cardId\":%d}".formatted(cardId)))
                .andExpect(status().isCreated());

        reset(session.token());

        assertThat(records.findById(recordId)).isEmpty();
    }

    @Test
    void expiredDemoSessionDeletesTheNewRecordGraphWithoutTouchingAnotherOwner() throws Exception {
        DemoSession expired = start("tech");
        DemoSession active = start("visual");
        long experimentId = createExperiment(expired.token(), "逾期清理測試");
        long recordId = createRecord(expired.token(), experimentId, "待清除紀錄");
        var expiredUser = users.findById(expired.userId()).orElseThrow();
        expiredUser.setDemoExpiresAt(ZonedDateTime.now().minusMinutes(1));
        users.saveAndFlush(expiredUser);

        demos.clearExpiredSessions();

        assertThat(users.findById(expired.userId())).isEmpty();
        assertThat(records.findById(recordId)).isEmpty();
        assertThat(users.findById(active.userId())).isPresent();
        assertThat(cards.findAllByUserId(active.userId())).hasSize(15);
    }

    private DemoSession start(String library) throws Exception {
        String body = mvc.perform(post("/api/auth/demo-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"library\":\"%s\"}".formatted(library)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var response = json.readTree(body);
        return new DemoSession(response.get("id").asLong(), response.get("token").asText());
    }

    private void reset(String token) throws Exception {
        mvc.perform(post("/api/auth/demo-sessions/reset").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private long createExperiment(String token, String title) throws Exception {
        String body = mvc.perform(post("/api/experiments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"%s\"}".formatted(title)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createRecord(String token, long experimentId, String title) throws Exception {
        String body = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"%s\",\"recordedOn\":\"2026-11-22\",\"understandingNode\":false,\"methodCandidate\":false,\"highlight\":\"GENERAL\",\"requestId\":\"%s\"}"
                                .formatted(title, UUID.randomUUID())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private long createTopic(String token, long experimentId, String name) throws Exception {
        String body = mvc.perform(post("/api/experiments/{experimentId}/topics", experimentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record DemoSession(long userId, String token) {}
}
