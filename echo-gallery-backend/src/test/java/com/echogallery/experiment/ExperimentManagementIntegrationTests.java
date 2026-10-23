package com.echogallery.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
class ExperimentManagementIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExperimentRepository experimentRepository;

    @BeforeEach
    void cleanDatabase() {
        experimentRepository.deleteAll();
    }

    @AfterEach
    void cleanExperimentData() {
        experimentRepository.deleteAll();
    }

    @Test
    void createAndUpdateExperimentPreservesHypothesisAndThemeColor() throws Exception {
        String token = register("experiment-owner", "experiment-owner@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "感恩日記",
                          "hypothesis": "持續記錄是否會改善生活感受？",
                          "description": "觀察一個月",
                          "themeColor": "LAVENDER"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("感恩日記"))
                .andExpect(jsonPath("$.hypothesis").value("持續記錄是否會改善生活感受？"))
                .andExpect(jsonPath("$.themeColor").value("LAVENDER"))
                .andReturn();

        long experimentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/experiments/{id}", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "title": "感恩日記實驗",
                          "hypothesis": "每天記錄三件事是否更容易看見正向經驗？",
                          "description": "調整觀察方式",
                          "themeColor": "AMBER"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hypothesis").value("每天記錄三件事是否更容易看見正向經驗？"))
                .andExpect(jsonPath("$.themeColor").value("AMBER"));
    }

    @Test
    void oldRequestWithoutNewFieldsUsesCompatibleDefaults() throws Exception {
        String token = register("compatible-owner", "compatible-owner@example.com");

        mockMvc.perform(post("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"舊格式實驗主題\",\"description\":\"仍可建立\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.themeColor").value("LEAF"));

        mockMvc.perform(get("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("舊格式實驗主題"));
    }

    @Test
    void allScopeIncludesActiveAndArchivedExperiments() throws Exception {
        String token = register("all-scope-owner", "all-scope-owner@example.com");
        createExperiment(token, "active experiment");
        long archivedId = createExperiment(token, "archived experiment");

        mockMvc.perform(put("/api/experiments/{id}/archive", archivedId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("archived", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void explorationCardIsCreatedWithoutAddingLegacySoilRelation() throws Exception {
        String token = register("exploration-card-owner", "exploration-card-owner@example.com");
        long experimentId = createExperiment(token, "exploration card test");

        mockMvc.perform(put("/api/experiments/{id}/exploration/current-try", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentTry\":\"try a small thing\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTry").value("try a small thing"));

        mockMvc.perform(get("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].currentTry").value("try a small thing"))
                .andExpect(jsonPath("$.content[0].explorationRecordCount").value(0));

        MvcResult recordResult = mockMvc.perform(post("/api/experiments/{id}/exploration/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"discovery\":\"found a useful change\",\"includeCurrentTry\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTry").value(""))
                .andExpect(jsonPath("$.records[0].tryText").value("try a small thing"))
                .andReturn();
        long recordId = objectMapper.readTree(recordResult.getResponse().getContentAsString())
                .get("records").get(0).get("id").asLong();

        mockMvc.perform(put("/api/experiments/{id}/exploration/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tryText\":\"updated try\",\"discovery\":\"updated useful change\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].tryText").value("updated try"))
                .andExpect(jsonPath("$.records[0].discovery").value("updated useful change"));

        mockMvc.perform(put("/api/experiments/{id}/exploration/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tryText\":\"updated try\",\"discovery\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].tryText").value("updated try"))
                .andExpect(jsonPath("$.records[0].discovery").value(""));

        mockMvc.perform(get("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].explorationRecordCount").value(1));

        MvcResult result = mockMvc.perform(post("/api/experiments/{id}/exploration/cards", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(("""
                        {
                          "type": "note",
                          "title": "exploration record card",
                          "content": "try: a small thing\\nfound: continue.",
                          "intervalDays": 10,
                          "recordIds": [%d]
                        }
                        """).formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("exploration record card"))
                .andReturn();
        long cardId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/experiments/{id}/cards", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("stage", "GROWING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/experiments/{id}/exploration", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].exports[0].cardId").value(cardId));

        long existingCardId = createCard(token, "existing experiment card");
        mockMvc.perform(post("/api/experiments/{id}/cards", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":%d,\"stage\":\"SEED\"}".formatted(existingCardId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/experiments/{id}/exploration/cards/{cardId}", experimentId, existingCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordIds\":[%d],\"content\":\"compiled exploration\"}".formatted(recordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("compiled exploration"));

        mockMvc.perform(post("/api/experiments/{id}/exploration/cards/{cardId}", experimentId, existingCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recordIds\":[%d],\"content\":\"compiled exploration again\"}".formatted(recordId)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/experiments/{id}/exploration", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].exports.length()").value(2));

        long materialOnlyExperimentId = createExperiment(token, "same card without exploration source");
        mockMvc.perform(post("/api/experiments/{id}/cards", materialOnlyExperimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":%d,\"stage\":\"SEED\"}".formatted(existingCardId)))
                .andExpect(status().isOk());

        MvcResult overviewResult = mockMvc.perform(get("/api/overview/recent")
                .param("periodDays", "30")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period.generativity.derivedCardCount").value(0))
                .andReturn();
        var materials = objectMapper.readTree(overviewResult.getResponse().getContentAsString())
                .get("period").get("recentExperimentMaterials");
        String explorationSourceKind = null;
        String materialSourceKind = null;
        for (var material : materials) {
            if (material.get("cardId").asLong() != existingCardId) {
                continue;
            }
            if (material.get("experimentId").asLong() == experimentId) {
                explorationSourceKind = material.get("sourceKind").asText();
            }
            if (material.get("experimentId").asLong() == materialOnlyExperimentId) {
                materialSourceKind = material.get("sourceKind").asText();
            }
        }
        assertEquals("EXPLORATION", explorationSourceKind);
        assertEquals("MATERIAL", materialSourceKind);

        mockMvc.perform(delete("/api/experiments/{id}/exploration/records/{recordId}", experimentId, recordId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(put("/api/experiments/{id}/exploration/current-try", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentTry\":\"another try\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/experiments/{id}/exploration/favorite-tries", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"favoriteTries\":[\"saved try\"]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/experiments/{id}/exploration/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"discovery\":\"another finding\",\"includeCurrentTry\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/experiments/{id}/exploration", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/experiments/{id}/exploration", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTry").value(""))
                .andExpect(jsonPath("$.favoriteTries").isEmpty())
                .andExpect(jsonPath("$.records").isEmpty());

        mockMvc.perform(get("/api/cards/{id}", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cards/{id}", existingCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void ownerCanDeleteExperimentAndOtherUserCannot() throws Exception {
        String ownerToken = register("experiment-delete-owner", "experiment-delete-owner@example.com");
        String otherToken = register("experiment-delete-other", "experiment-delete-other@example.com");
        long experimentId = createExperiment(ownerToken, "待刪除實驗主題");
        long sourceCardId = createCard(ownerToken, "刪除實驗主題後仍應保留的來源卡");

        mockMvc.perform(post("/api/experiments/{id}/cards", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "cardId": %d,
                          "stage": "SEED",
                          "note": "作為衍生來源"
                        }
                        """.formatted(sourceCardId)))
                .andExpect(status().isOk());

        MvcResult growResult = mockMvc.perform(post("/api/experiments/{id}/grow", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type": "note",
                          "title": "刪除實驗主題後仍應保留的衍生卡",
                          "intervalDays": 10,
                          "sourceCardIds": [%d],
                          "stage": "GROWING",
                          "note": "驗證思想脈絡隨主題移除"
                        }
                        """.formatted(sourceCardId)))
                .andExpect(status().isOk())
                .andReturn();
        long derivedCardId = objectMapper.readTree(growResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(delete("/api/experiments/{id}", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/experiments/{id}", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/experiments/{id}", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/cards/{id}", sourceCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/cards/{id}", derivedCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void legacyGardenEndpointsRemainCompatibleDuringMigration() throws Exception {
        String token = register("legacy-experiment-owner", "legacy-experiment-owner@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/gardens")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ExperimentPayload("相容實驗主題", "相容層仍可讀取"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("相容實驗主題"))
                .andReturn();
        long experimentId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();
        long cardId = createCard(token, "相容層卡片");

        mockMvc.perform(post("/api/gardens/{id}/cards", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "cardId": %d,
                          "stage": "SEED"
                        }
                        """.formatted(cardId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/cards/{id}/gardens", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].experimentId").value(experimentId))
                .andExpect(jsonPath("$[0].gardenId").value(experimentId))
                .andExpect(jsonPath("$[0].gardenTitle").value("相容實驗主題"));
    }

    @Test
    void explorationThreadKeepsSnapshotWhenSourceExplorationIsCleared() throws Exception {
        String token = register("thread-owner", "thread-owner@example.com");
        long experimentId = createExperiment(token, "thread test");

        MvcResult threadResult = mockMvc.perform(post("/api/experiments/{id}/threads", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"既有材料與線索\",\"status\":\"NOT_STARTED\",\"conclusion\":\"先整理既有方向\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andReturn();
        long threadId = objectMapper.readTree(threadResult.getResponse().getContentAsString()).get("id").asLong();

        long materialCardId = createCard(token, "thread material");
        mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/cards", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cardId\":%d,\"role\":\"MATERIAL\"}".formatted(materialCardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cards[0].role").value("MATERIAL"))
                .andExpect(jsonPath("$.cards[0].cardTitle").value("thread material"))
                .andExpect(jsonPath("$.cards[0].cardType").value("note"));
        mockMvc.perform(get("/api/cards/{cardId}/exploration-threads", materialCardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].threadId").value(threadId))
                .andExpect(jsonPath("$[0].experimentId").value(experimentId));

        mockMvc.perform(put("/api/experiments/{id}/exploration/current-try", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentTry\":\"每天散步二十分鐘\"}"))
                .andExpect(status().isOk());
        MvcResult explorationResult = mockMvc.perform(post("/api/experiments/{id}/exploration/records", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"discovery\":\"傍晚比較容易持續\",\"includeCurrentTry\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long sourceRecordId = objectMapper.readTree(explorationResult.getResponse().getContentAsString())
                .get("records").get(0).get("id").asLong();

        mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/imports", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items":[{
                          "recordId":%d,
                          "includeMethod":true,
                          "includeDiscovery":false,
                          "methodContent":"   "
                        }]}
                        """.formatted(sourceRecordId)))
                .andExpect(status().isBadRequest());

        MvcResult importResult = mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/imports", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"items":[{
                          "recordId":%d,
                          "includeMethod":true,
                          "includeDiscovery":true,
                          "methodContent":"每天傍晚散步二十分鐘",
                          "discovery":"傍晚比較容易持續"
                        }]}
                        """.formatted(sourceRecordId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods[0].sourceRecordId").value(sourceRecordId))
                .andExpect(jsonPath("$.records[0].sourceRecordId").value(sourceRecordId))
                .andReturn();
        long methodId = objectMapper.readTree(importResult.getResponse().getContentAsString())
                .get("methods").get(0).get("id").asLong();

        mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/methods", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"重複來源試法\",\"sourceRecordId\":%d}".formatted(sourceRecordId)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/records", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"discovery\":\"重複來源發現\",\"sourceRecordId\":%d}".formatted(sourceRecordId)))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/experiments/{id}/threads/{threadId}/imports", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"recordId\":%d,\"includeMethod\":false,\"includeDiscovery\":true,\"discovery\":\"傍晚比較容易持續\"}]}"
                        .formatted(sourceRecordId)))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/experiments/{id}/exploration", experimentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/experiments/{id}/threads/{threadId}", experimentId, threadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods[0].content").value("每天傍晚散步二十分鐘"))
                .andExpect(jsonPath("$.methods[0].sourceRecordId").doesNotExist())
                .andExpect(jsonPath("$.records[0].discovery").value("傍晚比較容易持續"))
                .andExpect(jsonPath("$.records[0].methodId").value(methodId))
                .andExpect(jsonPath("$.records[0].sourceRecordId").doesNotExist());
    }

    @Test
    void threadSummaryPageReturnsOnlyTheListPreviewAndCounts() throws Exception {
        String token = register("thread-summary", "thread-summary@example.com");
        long experimentId = createExperiment(token, "探索線摘要");
        long cardId = createCard(token, "摘要材料");
        String root = "/api/experiments/" + experimentId + "/threads";
        MvcResult created = mockMvc.perform(post(root + "/materials")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"摘要方向\",\"cardId\":%d}".formatted(cardId)))
                .andExpect(status().isOk()).andReturn();
        long firstThreadId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        MvcResult second = mockMvc.perform(post(root)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"第二條方向\"}"))
                .andExpect(status().isOk()).andReturn();
        long secondThreadId = objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get(root + "/summaries?page=0&size=20")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].name").value("摘要方向"))
                .andExpect(jsonPath("$.content[0].materialCount").value(1))
                .andExpect(jsonPath("$.content[0].recentMaterials[0].title").value("摘要材料"));

        mockMvc.perform(put(root + "/{threadId}/position", firstThreadId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"targetThreadId\":%d,\"after\":true}".formatted(secondThreadId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(root + "/summaries?page=0&size=20")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(secondThreadId))
                .andExpect(jsonPath("$.content[1].id").value(firstThreadId));
    }

    @Test
    void threadDiscoveryMaterialBatchAndReverseLinksKeepOwnershipAndDeleteSafely() throws Exception {
        String token = register("thread-flow", "thread-flow@example.com");
        String other = register("thread-stranger", "thread-stranger@example.com");
        long experimentId = createExperiment(token, "探索線操作");
        long cardId = createCard(token, "材料摘要");
        long secondCard = createCard(token, "第二張材料");
        long foreignCard = createCard(other, "其他人的材料");
        String root = "/api/experiments/" + experimentId + "/threads";
        MvcResult created = mockMvc.perform(post(root + "/materials")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"整理方向\",\"cardId\":%d}".formatted(cardId)))
                .andExpect(status().isOk()).andReturn();
        long threadId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        String thread = root + "/" + threadId;
        long linkId = objectMapper.readTree(created.getResponse().getContentAsString()).get("cards").get(0).get("id").asLong();
        mockMvc.perform(put(thread + "/cards/" + linkId + "/note")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"用於比較不同方向\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards[0].note").value("用於比較不同方向"));
        mockMvc.perform(put(thread + "/cards/" + linkId + "/note")
                .header(HttpHeaders.AUTHORIZATION, bearer(other)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"不可修改\"}"))
                .andExpect(status().isNotFound());
        // 任一材料不屬於使用者時，整批回滾，不能只加入前半批。
        mockMvc.perform(post(thread + "/cards/batch")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MATERIAL\",\"cardIds\":[%d,%d]}".formatted(secondCard, foreignCard)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(thread).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.cards.length()").value(1));
        mockMvc.perform(post(thread + "/cards/batch")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"MATERIAL\",\"cardIds\":[%d]}".formatted(secondCard)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cards.length()").value(2));
        MvcResult method = mockMvc.perform(post(thread + "/methods")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"每天散步\"}"))
                .andExpect(status().isOk()).andReturn();
        long methodId = objectMapper.readTree(method.getResponse().getContentAsString()).get("methods").get(0).get("id").asLong();
        mockMvc.perform(post(thread + "/discoveries")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"methodId\":%d,\"tryText\":\"每天散步\",\"discovery\":\"比較容易開始工作\"}".formatted(methodId)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.records[0].methodId").value(methodId));
        MvcResult original = mockMvc.perform(get("/api/experiments/" + experimentId + "/exploration")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.records[0].discovery").value("比較容易開始工作"))
                .andExpect(jsonPath("$.records[0].sourceThreadMethodId").value(methodId)).andReturn();
        long recordId = objectMapper.readTree(original.getResponse().getContentAsString()).get("records").get(0).get("id").asLong();
        mockMvc.perform(post(root + "/imports")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"不得留下空探索線\",\"items\":[{\"recordId\":%d,\"includeMethod\":true,\"includeDiscovery\":false,\"methodContent\":\"   \"}]}".formatted(recordId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(root).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.length()").value(1));
        MvcResult work = mockMvc.perform(post("/api/creative-works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"探索成果\",\"editorMode\":\"INTERNAL\"}"))
                .andExpect(status().isOk()).andReturn();
        long workId = objectMapper.readTree(work.getResponse().getContentAsString()).get("work").get("id").asLong();
        mockMvc.perform(post(thread + "/works").header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content("{\"workId\":%d}".formatted(workId)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/creative-works/" + workId + "/exploration-threads")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$[0].threadId").value(threadId));
        mockMvc.perform(get("/api/creative-works/" + workId + "/exploration-threads")
                .header(HttpHeaders.AUTHORIZATION, bearer(other))).andExpect(status().isNotFound());
        mockMvc.perform(delete(thread).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/creative-works/" + workId + "/exploration-threads")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/experiments/" + experimentId + "/exploration")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.records[0].discovery").value("比較容易開始工作"))
                .andExpect(jsonPath("$.records[0].sourceThreadMethodId").doesNotExist());
        mockMvc.perform(get("/api/cards/" + cardId).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isOk());
        mockMvc.perform(delete("/api/creative-works/" + workId).header(HttpHeaders.AUTHORIZATION, bearer(token))).andExpect(status().isNoContent());
    }

    private long createExperiment(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/experiments")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ExperimentPayload(title, "待確認假設"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createCard(String token, String title) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "type": "note",
                          "title": "%s",
                          "intervalDays": 10
                        }
                        """.formatted(title)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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

    private record RegistrationRequest(String username, String email, String password) {}

    private record ExperimentPayload(String title, String hypothesis) {}
}
