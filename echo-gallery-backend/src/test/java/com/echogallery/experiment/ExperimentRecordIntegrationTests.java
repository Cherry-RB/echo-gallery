package com.echogallery.experiment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.echogallery.support.IntegrationTestBase;

import tools.jackson.databind.ObjectMapper;

/** 驗證 T2 的實際請求綁定、owner 隔離、重送與版本衝突。 */
@AutoConfigureMockMvc
class ExperimentRecordIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ExperimentRecordRepository records;
    @Autowired ExperimentRecordReferenceRepository references;
    @Autowired ExperimentRepository experiments;
    @Autowired com.echogallery.card.CardRepository cards;
    @Autowired com.echogallery.work.WorkSourceRepository workSources;
    @Autowired ExperimentRecordTopicRepository recordTopics;

    @AfterEach
    void clean() {
        workSources.deleteAll();
        recordTopics.deleteAll();
        references.deleteAll();
        records.deleteAll();
        experiments.deleteAll();
    }

    @Test
    void keepsUrlReferencesInsideTheOwnedRecord() throws Exception {
        String owner = register("reference-owner");
        String stranger = register("reference-stranger");
        long experimentId = createExperiment(owner);
        String created = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"紀錄\",\"recordedOn\":\"2026-11-21\",\"understandingNode\":false,\"methodCandidate\":false,\"highlight\":\"GENERAL\",\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long recordId = json.readTree(created).get("id").asLong();

        var saved = mvc.perform(post("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"URL\",\"url\":\"https://example.com/reference\",\"label\":\"參考\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.url").value("https://example.com/reference")).andReturn();
        long referenceId = json.readTree(saved.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(referenceId));
        mvc.perform(delete("/api/experiments/{experimentId}/records/{recordId}/references/{referenceId}", experimentId, recordId, referenceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(post("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"URL\",\"url\":\"not-a-url\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsCardReferenceAtomicallyWithTheRecord() throws Exception {
        String owner = register("record-card-owner");
        long experimentId = createExperiment(owner);
        long cardId = createCard(owner, "來源卡片");
        String payload = """
                {"cardId":%d,"record":{"title":"從卡片記下","content":null,"practiceText":null,
                "recordedOn":"2026-11-21","understandingNode":false,"methodCandidate":false,
                "highlight":"GENERAL","requestId":"%s"}}
                """.formatted(cardId, UUID.randomUUID());

        var created = mvc.perform(post("/api/experiments/{experimentId}/records/from-card", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("從卡片記下")).andReturn();
        long recordId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").value(cardId));

        mvc.perform(post("/api/experiments/{experimentId}/records/from-card", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content(payload.replace("\"cardId\":" + cardId, "\"cardId\":999999999")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deletingAReferencedCardOnlyRemovesTheReference() throws Exception {
        String owner = register("record-card-delete-owner");
        long experimentId = createExperiment(owner);
        long cardId = createCard(owner, "可刪除的來源卡片");
        long recordId = createRecord(owner, experimentId, "保留的紀錄");
        mvc.perform(post("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CARD\",\"cardId\":" + cardId + "}"))
                .andExpect(status().isCreated());

        cards.deleteById(cardId);
        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}/references", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void filtersMultipleTopicsWithAnyOrAllSemantics() throws Exception {
        String owner = register("record-topic-filter-owner");
        long experimentId = createExperiment(owner);
        long firstRecord = createRecord(owner, experimentId, "第一筆");
        long secondRecord = createRecord(owner, experimentId, "第二筆");
        long firstTopic = createTopic(owner, experimentId, "理解");
        long secondTopic = createTopic(owner, experimentId, "練習");
        setTopics(owner, experimentId, firstRecord, firstTopic, secondTopic);
        setTopics(owner, experimentId, secondRecord, firstTopic);

        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).param("topicIds", firstTopic + "," + secondTopic).param("topicMatch", "ANY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).param("topicIds", firstTopic + "," + secondTopic).param("topicMatch", "ALL"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).param("topicIds", String.valueOf(firstTopic)).param("unclassified", "true"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsRecordTopicsWithoutExtraClientRequestsAndPersistsTopicDirectoryOrder() throws Exception {
        String owner = register("record-topic-directory-owner");
        long experimentId = createExperiment(owner);
        long recordId = createRecord(owner, experimentId, "整理主題");
        long firstTopic = createTopic(owner, experimentId, "開始阻力");
        long secondTopic = createTopic(owner, experimentId, "練習節奏");
        setTopics(owner, experimentId, recordId, secondTopic);

        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].topics[0].id").value(secondTopic))
                .andExpect(jsonPath("$.content[0].topics[0].name").value("練習節奏"));

        mvc.perform(put("/api/experiments/{experimentId}/topics/order", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicIds\":[" + secondTopic + "," + firstTopic + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(secondTopic))
                .andExpect(jsonPath("$[0].displayOrder").value(1));
        mvc.perform(get("/api/experiments/{experimentId}/topics", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(secondTopic));
    }

    @Test
    void replacesOverlappingTopicsAndRenamesOwnedTopic() throws Exception {
        String owner = register("record-topic-replace-owner");
        long experimentId = createExperiment(owner);
        long recordId = createRecord(owner, experimentId, "多主題紀錄");
        long firstTopic = createTopic(owner, experimentId, "第一主題");
        long secondTopic = createTopic(owner, experimentId, "第二主題");
        setTopics(owner, experimentId, recordId, firstTopic);

        setTopics(owner, experimentId, recordId, firstTopic, secondTopic);
        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}/topics", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(put("/api/experiments/{experimentId}/topics/{topicId}", experimentId, secondTopic)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"重新命名主題\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(secondTopic))
                .andExpect(jsonPath("$.name").value("重新命名主題"));
    }

    @Test
    void savesRecordContentAndTopicsInTheSameTransaction() throws Exception {
        String owner = register("record-topic-editor-owner");
        long experimentId = createExperiment(owner);
        long otherExperimentId = createExperiment(owner);
        long firstTopic = createTopic(owner, experimentId, "開始阻力");
        long secondTopic = createTopic(owner, experimentId, "練習節奏");
        long foreignTopic = createTopic(owner, otherExperimentId, "其他實驗主題");

        var created = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"先開始\",\"recordedOn\":\"2026-11-21\",\"understandingNode\":false,\"methodCandidate\":false,\"highlight\":\"GENERAL\",\"topicIds\":[" + firstTopic + "],\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics[0].id").value(firstTopic)).andReturn();
        long recordId = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(put("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"完成更新\",\"recordedOn\":\"2026-11-22\",\"understandingNode\":false,\"methodCandidate\":true,\"highlight\":\"GAIN\",\"topicIds\":[" + secondTopic + "],\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("完成更新"))
                .andExpect(jsonPath("$.topics[0].id").value(secondTopic));

        mvc.perform(put("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"不應寫入\",\"recordedOn\":\"2026-11-22\",\"understandingNode\":false,\"methodCandidate\":true,\"highlight\":\"GAIN\",\"topicIds\":[" + foreignTopic + "],\"expectedVersion\":1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("完成更新"))
                .andExpect(jsonPath("$.topics[0].id").value(secondTopic));
    }

    @Test
    void createsRetriesUpdatesAndRejectsForeignOrStaleRequests() throws Exception {
        String owner = register("record-owner");
        String stranger = register("record-stranger");
        long experimentId = createExperiment(owner);
        String requestId = UUID.randomUUID().toString();
        String payload = """
                {"title":"先畫五分鐘","content":null,"practiceText":null,
                 "recordedOn":"2026-11-21","understandingNode":true,"methodCandidate":false,
                 "highlight":"GAIN","requestId":"%s"}
                """.formatted(requestId);

        var first = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("先畫五分鐘"))
                .andExpect(jsonPath("$.version").value(0)).andReturn();
        long recordId = json.readTree(first.getResponse().getContentAsString()).get("id").asLong();

        mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(recordId));
        mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(stranger)).contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(stranger))).andExpect(status().isNotFound());
        mvc.perform(put("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":null,\"content\":\"已經開始了\",\"practiceText\":\"先畫五分鐘\",\"recordedOn\":\"2026-11-21\",\"understandingNode\":true,\"methodCandidate\":true,\"highlight\":\"GAIN\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/experiments/{experimentId}/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(owner)).param("expectedVersion", "0"))
                .andExpect(status().isConflict());
    }

    private String register(String username) throws Exception {
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new Registration(username, username + "@example.com", "password123"))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createExperiment(String token) throws Exception {
        var result = mvc.perform(post("/api/experiments").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"開始畫畫\",\"startingPoint\":\"很難開始\",\"desiredOutcome\":\"每天畫十分鐘\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.startingPoint").value("很難開始"))
                .andExpect(jsonPath("$.desiredOutcome").value("每天畫十分鐘")).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createCard(String token, String title) throws Exception {
        var result = mvc.perform(post("/api/cards").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"note\",\"title\":\"" + title + "\",\"intervalDays\":10}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createRecord(String token, long experimentId, String title) throws Exception {
        var result = mvc.perform(post("/api/experiments/{experimentId}/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"recordedOn\":\"2026-11-21\",\"understandingNode\":false,\"methodCandidate\":false,\"highlight\":\"GENERAL\",\"requestId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createTopic(String token, long experimentId, String name) throws Exception {
        var result = mvc.perform(post("/api/experiments/{experimentId}/topics", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void setTopics(String token, long experimentId, long recordId, long... topicIds) throws Exception {
        String ids = java.util.Arrays.stream(topicIds).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        mvc.perform(put("/api/experiments/{experimentId}/records/{recordId}/topics", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"topicIds\":[" + ids + "]}"))
                .andExpect(status().isOk());
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Registration(String username, String email, String password) {}
}
