package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
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

import com.echogallery.card.Card;
import com.echogallery.card.CardGrowthStatus;
import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class WorkCardManagementIntegrationTests extends IntegrationTestBase {

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
    void addCardCreatesCandidateRelation() throws Exception {
        String token = register("relation-owner", "relation-owner@example.com");
        long workId = createWork(token, "關聯作品");
        long cardId = createCard(token, "候選素材");

        mockMvc.perform(post("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, "  核心論點  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workId").value(workId))
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.status").value("CANDIDATE"))
                .andExpect(jsonPath("$.note").value("核心論點"))
                .andExpect(jsonPath("$.linkedAt").exists())
                .andExpect(jsonPath("$.usedAt").doesNotExist());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId)).isPresent();
    }

    @Test
    void duplicateRelationReturnsConflict() throws Exception {
        String token = register("duplicate-owner", "duplicate-owner@example.com");
        long workId = createWork(token, "不可重複作品");
        long cardId = createCard(token, "不可重複素材");
        addCard(token, workId, cardId);

        mockMvc.perform(post("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, null)))
                .andExpect(status().isConflict());

        assertThat(workCardRepository.count()).isEqualTo(1);
    }

    @Test
    void relationRequiresOwnershipOfBothWorkAndCard() throws Exception {
        String firstToken = register("first-owner", "first-owner@example.com");
        String secondToken = register("second-owner", "second-owner@example.com");
        long firstWorkId = createWork(firstToken, "第一位使用者的作品");
        long firstCardId = createCard(firstToken, "第一位使用者的卡片");
        long secondCardId = createCard(secondToken, "第二位使用者的卡片");

        mockMvc.perform(post("/api/works/{workId}/cards", firstWorkId)
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(secondCardId, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/works/{workId}/cards", firstWorkId)
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(firstCardId, null)))
                .andExpect(status().isForbidden());

        assertThat(workCardRepository.count()).isZero();
    }

    @Test
    void removeRelationKeepsWorkAndCard() throws Exception {
        String token = register("unlink-owner", "unlink-owner@example.com");
        long workId = createWork(token, "保留的作品");
        long cardId = createCard(token, "保留的卡片");
        addCard(token, workId, cardId);

        mockMvc.perform(delete("/api/works/{workId}/cards/{cardId}", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId)).isEmpty();
        assertThat(workRepository.findById(workId)).isPresent();
        assertThat(cardRepository.findById(cardId)).isPresent();
    }

    @Test
    void anotherUserCannotRemoveRelation() throws Exception {
        String ownerToken = register("unlink-relation-owner", "unlink-relation-owner@example.com");
        String otherToken = register("unlink-relation-other", "unlink-relation-other@example.com");
        long workId = createWork(ownerToken, "私人作品");
        long cardId = createCard(ownerToken, "私人卡片");
        addCard(ownerToken, workId, cardId);

        mockMvc.perform(delete("/api/works/{workId}/cards/{cardId}", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId)).isPresent();
    }

    @Test
    void missingCardIdIsRejected() throws Exception {
        String token = register("invalid-relation", "invalid-relation@example.com");
        long workId = createWork(token, "驗證作品");

        mockMvc.perform(post("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(workCardRepository.count()).isZero();
    }

    @Test
    void statusTransitionSetsPreservesAndClearsUsedAtWithoutChangingCardGrowth() throws Exception {
        String token = register("status-relation-owner", "status-relation-owner@example.com");
        long workId = createWork(token, "狀態切換作品");
        long cardId = createCard(token, "狀態切換素材");
        addCard(token, workId, cardId);

        JsonNode firstUsedResponse = updateStatus(token, workId, cardId, "USED");
        String firstUsedAt = firstUsedResponse.get("usedAt").asText();
        assertThat(firstUsedResponse.get("status").asText()).isEqualTo("USED");
        assertThat(firstUsedAt).isNotBlank();
        java.time.ZonedDateTime persistedUsedAt = workCardRepository
                .findByWorkIdAndCardId(workId, cardId)
                .orElseThrow()
                .getUsedAt();

        JsonNode repeatedUsedResponse = updateStatus(token, workId, cardId, "USED");
        assertThat(repeatedUsedResponse.get("usedAt").asText()).isNotBlank();
        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getUsedAt)
                .isEqualTo(persistedUsedAt);

        JsonNode candidateResponse = updateStatus(token, workId, cardId, "CANDIDATE");
        assertThat(candidateResponse.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidateResponse.get("usedAt").isNull()).isTrue();

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(card -> card.getGrowthStatus())
                .isEqualTo(CardGrowthStatus.SEED);
    }

    @Test
    void anotherUserCannotUpdateRelationStatus() throws Exception {
        String ownerToken = register("status-owner", "status-owner@example.com");
        String otherToken = register("status-other", "status-other@example.com");
        long workId = createWork(ownerToken, "私人狀態作品");
        long cardId = createCard(ownerToken, "私人狀態素材");
        addCard(ownerToken, workId, cardId);

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/status", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest("USED"))))
                .andExpect(status().isForbidden());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getStatus)
                .isEqualTo(WorkCardStatus.CANDIDATE);
    }

    @Test
    void missingStatusIsRejectedWithoutChangingRelation() throws Exception {
        String token = register("invalid-status-owner", "invalid-status-owner@example.com");
        long workId = createWork(token, "驗證狀態作品");
        long cardId = createCard(token, "驗證狀態素材");
        addCard(token, workId, cardId);

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/status", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getStatus)
                .isEqualTo(WorkCardStatus.CANDIDATE);
    }

    @Test
    void updateNoteNormalizesAndClearsRelationNote() throws Exception {
        String token = register("note-owner", "note-owner@example.com");
        long workId = createWork(token, "備註作品");
        long cardId = createCard(token, "備註素材");
        addCard(token, workId, cardId);

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/note", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("  用於開場論點  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("用於開場論點"));

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getNote)
                .isEqualTo("用於開場論點");

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/note", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").doesNotExist());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getNote)
                .isNull();
    }

    @Test
    void updateNoteValidatesLengthAndRelationOwnership() throws Exception {
        String ownerToken = register("note-private-owner", "note-private-owner@example.com");
        String otherToken = register("note-private-other", "note-private-other@example.com");
        long workId = createWork(ownerToken, "私人備註作品");
        long cardId = createCard(ownerToken, "私人備註素材");
        addCard(ownerToken, workId, cardId);

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/note", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("不應寫入"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/note", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("a".repeat(1001)))))
                .andExpect(status().isBadRequest());

        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId))
                .get()
                .extracting(WorkCard::getNote)
                .isNull();
    }

    @Test
    void workCardListReturnsCardDisplayDataAndRelationStatus() throws Exception {
        String token = register("list-owner", "list-owner@example.com");
        long workId = createWork(token, "素材列表作品");
        long candidateCardId = createCard(token, "候選卡片", List.of("AI", "Java"));
        long usedCardId = createCard(token, "已使用卡片", List.of("創作"));
        addCard(token, workId, candidateCardId);
        addCard(token, workId, usedCardId);
        updateStatus(token, workId, usedCardId, "USED");

        MvcResult result = mockMvc.perform(get("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode candidate = findRelation(response, candidateCardId);
        assertThat(candidate.get("cardTitle").asText()).isEqualTo("候選卡片");
        assertThat(candidate.get("cardType").asText()).isEqualTo("note");
        assertThat(candidate.get("cardGrowthStatus").asText()).isEqualTo("SEED");
        assertThat(candidate.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidate.get("tags").get(0).asText()).isEqualTo("AI");
        assertThat(candidate.get("tags").get(1).asText()).isEqualTo("Java");

        JsonNode used = findRelation(response, usedCardId);
        assertThat(used.get("status").asText()).isEqualTo("USED");
        assertThat(used.get("usedAt").isNull()).isFalse();
    }

    @Test
    void workCardListReturnsEmptyArrayWhenWorkHasNoCards() throws Exception {
        String token = register("empty-list-owner", "empty-list-owner@example.com");
        long workId = createWork(token, "空素材作品");

        mockMvc.perform(get("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anotherUserCannotReadWorkCardList() throws Exception {
        String ownerToken = register("private-list-owner", "private-list-owner@example.com");
        String otherToken = register("private-list-other", "private-list-other@example.com");
        long workId = createWork(ownerToken, "私人素材列表");
        long cardId = createCard(ownerToken, "私人素材");
        addCard(ownerToken, workId, cardId);

        mockMvc.perform(get("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cardWorkListReturnsCandidateAndUsedRelations() throws Exception {
        String token = register("card-work-owner", "card-work-owner@example.com");
        long cardId = createCard(token, "可重複使用素材");
        long candidateWorkId = createWork(token, "候選作品");
        long usedWorkId = createWork(token, "已採用作品");
        addCard(token, candidateWorkId, cardId);
        addCard(token, usedWorkId, cardId);
        updateStatus(token, usedWorkId, cardId, "USED");

        MvcResult result = mockMvc.perform(get("/api/cards/{cardId}/works", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode candidate = findCardWork(response, candidateWorkId);
        assertThat(candidate.get("workTitle").asText()).isEqualTo("候選作品");
        assertThat(candidate.get("workStatus").asText()).isEqualTo("IDEA");
        assertThat(candidate.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidate.get("usedAt").isNull()).isTrue();

        JsonNode used = findCardWork(response, usedWorkId);
        assertThat(used.get("workTitle").asText()).isEqualTo("已採用作品");
        assertThat(used.get("status").asText()).isEqualTo("USED");
        assertThat(used.get("usedAt").isNull()).isFalse();
    }

    @Test
    void cardWorkListIsEmptyWithoutRelationsAndRejectsOtherUsers() throws Exception {
        String ownerToken = register("card-work-private-owner", "card-work-private-owner@example.com");
        String otherToken = register("card-work-private-other", "card-work-private-other@example.com");
        long cardId = createCard(ownerToken, "私人卡片");

        mockMvc.perform(get("/api/cards/{cardId}/works", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/cards/{cardId}/works", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void workListReturnsCandidateAndUsedCountsPerWorkAndUser() throws Exception {
        String firstToken = register("count-first-owner", "count-first-owner@example.com");
        String secondToken = register("count-second-owner", "count-second-owner@example.com");
        long populatedWorkId = createWork(
                firstToken,
                "有素材作品",
                "把候選素材整理成一篇文章",
                "https://example.com/work");
        long emptyWorkId = createWork(firstToken, "空素材作品");
        long otherWorkId = createWork(secondToken, "其他使用者作品");
        long candidateCardId = createCard(firstToken, "候選計數素材");
        long usedCardId = createCard(firstToken, "使用計數素材");
        long otherCardId = createCard(secondToken, "其他使用者素材");
        addCard(firstToken, populatedWorkId, candidateCardId);
        addCard(firstToken, populatedWorkId, usedCardId);
        updateStatus(firstToken, populatedWorkId, usedCardId, "USED");
        addCard(secondToken, otherWorkId, otherCardId);
        updateStatus(secondToken, otherWorkId, otherCardId, "USED");

        MvcResult result = mockMvc.perform(get("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode populatedWork = findWork(response, populatedWorkId);
        assertThat(populatedWork.get("description").asText()).isEqualTo("把候選素材整理成一篇文章");
        assertThat(populatedWork.get("externalUrl").asText()).isEqualTo("https://example.com/work");
        assertThat(populatedWork.get("candidateCount").asLong()).isEqualTo(1);
        assertThat(populatedWork.get("usedCount").asLong()).isEqualTo(1);

        JsonNode emptyWork = findWork(response, emptyWorkId);
        assertThat(emptyWork.get("candidateCount").asLong()).isZero();
        assertThat(emptyWork.get("usedCount").asLong()).isZero();
    }

    @Test
    void cardBoardsAndInteractionsKeepWorkRelationAndGrowthStatus() throws Exception {
        String token = register("regression-owner", "regression-owner@example.com");
        long workId = createWork(token, "回歸驗收作品");
        long cardId = createCard(token, "回歸驗收卡片");
        addCard(token, workId, cardId);

        Card card = cardRepository.findById(cardId).orElseThrow();
        card.setNextShowAt(ZonedDateTime.now(ZoneId.of("Asia/Taipei")).minusDays(1));
        card.setSnoozeCount(11);
        cardRepository.saveAndFlush(card);

        assertCardListContains(token, "today", cardId);
        assertCardListContains(token, "all", cardId);
        assertCardListContains(token, "snoozed", cardId);

        mockMvc.perform(put("/api/cards/{id}/star", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"starStatus\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.likeCount").value(1))
                .andExpect(jsonPath("$.growthStatus").value("SEED"));

        mockMvc.perform(put("/api/cards/{id}/snooze", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextIntervalDays\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextShowAt").exists())
                .andExpect(jsonPath("$.growthStatus").value("SEED"));

        mockMvc.perform(put("/api/cards/{id}/read", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.growthStatus").value("SEED"));

        mockMvc.perform(put("/api/cards/{id}/archive", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archivedStatus\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true));

        assertCardListContains(token, "archived", cardId);
        assertCardListExcludes(token, "all", cardId);

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkUpdateRequest(
                        "封存後仍保留素材",
                        null,
                        null,
                        "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].cardId").value(cardId))
                .andExpect(jsonPath("$[0].status").value("CANDIDATE"))
                .andExpect(jsonPath("$[0].cardGrowthStatus").value("SEED"));

        assertThat(cardRepository.existsById(cardId)).isTrue();
        assertThat(workCardRepository.findByWorkIdAndCardId(workId, cardId)).isPresent();
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

    private long createWork(String token, String title) throws Exception {
        return createWork(token, title, null, null);
    }

    private long createWork(String token, String title, String description, String externalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkRequest(title, description, externalUrl))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createCard(String token, String title) throws Exception {
        return createCard(token, title, List.of());
    }

    private long createCard(String token, String title, List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CardRequest(
                        "note",
                        title,
                        null,
                        null,
                        null,
                        null,
                        null,
                        tags,
                        10))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void addCard(String token, long workId, long cardId) throws Exception {
        mockMvc.perform(post("/api/works/{workId}/cards", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, null)))
                .andExpect(status().isOk());
    }

    private JsonNode updateStatus(String token, long workId, long cardId, String statusValue) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/works/{workId}/cards/{cardId}/status", workId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest(statusValue))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode findRelation(JsonNode relations, long cardId) {
        for (JsonNode relation : relations) {
            if (relation.get("cardId").asLong() == cardId) {
                return relation;
            }
        }
        throw new AssertionError("找不到 cardId=" + cardId + " 的作品素材關聯");
    }

    private JsonNode findWork(JsonNode works, long workId) {
        for (JsonNode work : works) {
            if (work.get("id").asLong() == workId) {
                return work;
            }
        }
        throw new AssertionError("找不到 id=" + workId + " 的作品");
    }

    private JsonNode findCardWork(JsonNode relations, long workId) {
        for (JsonNode relation : relations) {
            if (relation.get("workId").asLong() == workId) {
                return relation;
            }
        }
        throw new AssertionError("找不到 workId=" + workId + " 的卡片作品關聯");
    }

    private void assertCardListContains(String token, String boardType, long cardId) throws Exception {
        JsonNode cards = getCardList(token, boardType);
        assertThat(cards).anyMatch(card -> card.get("id").asLong() == cardId);
    }

    private void assertCardListExcludes(String token, String boardType, long cardId) throws Exception {
        JsonNode cards = getCardList(token, boardType);
        assertThat(cards).noneMatch(card -> card.get("id").asLong() == cardId);
    }

    private JsonNode getCardList(String token, String boardType) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CardListRequest(
                        1,
                        20,
                        boardType,
                        10))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String addCardJson(long cardId, String note) throws Exception {
        return objectMapper.writeValueAsString(new AddCardRequest(cardId, note));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record WorkRequest(String title, String description, String externalUrl) {}

    private record WorkUpdateRequest(
            String title,
            String description,
            String externalUrl,
            String status
    ) {}

    private record AddCardRequest(Long cardId, String note) {}

    private record StatusRequest(String status) {}

    private record NoteRequest(String note) {}

    private record CardListRequest(
            Integer pageNumber,
            Integer pageSize,
            String boardType,
            Integer threshold
    ) {}

    private record CardRequest(
            String type,
            String title,
            String coverImageUrl,
            String url,
            String summary,
            String content,
            String reason,
            List<String> tags,
            Integer intervalDays
    ) {}
}
