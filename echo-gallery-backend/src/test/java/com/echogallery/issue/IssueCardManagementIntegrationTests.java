package com.echogallery.issue;

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
class IssueCardManagementIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IssueCardRepository issueCardRepository;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        issueCardRepository.deleteAll();
        issueRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void addCardCreatesCandidateRelation() throws Exception {
        String token = register("relation-owner", "relation-owner@example.com");
        long issueId = createIssue(token, "關聯作品");
        long cardId = createCard(token, "候選素材");

        mockMvc.perform(post("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, "  核心論點  ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issueId").value(issueId))
                .andExpect(jsonPath("$.cardId").value(cardId))
                .andExpect(jsonPath("$.status").value("CANDIDATE"))
                .andExpect(jsonPath("$.note").value("核心論點"))
                .andExpect(jsonPath("$.linkedAt").exists())
                .andExpect(jsonPath("$.usedAt").doesNotExist());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId)).isPresent();
    }

    @Test
    void duplicateRelationReturnsConflict() throws Exception {
        String token = register("duplicate-owner", "duplicate-owner@example.com");
        long issueId = createIssue(token, "不可重複作品");
        long cardId = createCard(token, "不可重複素材");
        addCard(token, issueId, cardId);

        mockMvc.perform(post("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, null)))
                .andExpect(status().isConflict());

        assertThat(issueCardRepository.count()).isEqualTo(1);
    }

    @Test
    void relationRequiresOwnershipOfBothIssueAndCard() throws Exception {
        String firstToken = register("first-owner", "first-owner@example.com");
        String secondToken = register("second-owner", "second-owner@example.com");
        long firstIssueId = createIssue(firstToken, "第一位使用者的作品");
        long firstCardId = createCard(firstToken, "第一位使用者的卡片");
        long secondCardId = createCard(secondToken, "第二位使用者的卡片");

        mockMvc.perform(post("/api/issues/{issueId}/cards", firstIssueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(secondCardId, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/issues/{issueId}/cards", firstIssueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(secondToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(firstCardId, null)))
                .andExpect(status().isForbidden());

        assertThat(issueCardRepository.count()).isZero();
    }

    @Test
    void removeRelationKeepsIssueAndCard() throws Exception {
        String token = register("unlink-owner", "unlink-owner@example.com");
        long issueId = createIssue(token, "保留的作品");
        long cardId = createCard(token, "保留的卡片");
        addCard(token, issueId, cardId);

        mockMvc.perform(delete("/api/issues/{issueId}/cards/{cardId}", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId)).isEmpty();
        assertThat(issueRepository.findById(issueId)).isPresent();
        assertThat(cardRepository.findById(cardId)).isPresent();
    }

    @Test
    void deleteIssueRemovesMaterialRelationsButKeepsOriginalCards() throws Exception {
        String token = register("delete-issue-card-owner", "delete-issue-card-owner@example.com");
        long issueId = createIssue(token, "待刪除議題");
        long cardId = createCard(token, "仍應保留的卡片");
        addCard(token, issueId, cardId);

        mockMvc.perform(delete("/api/issues/{issueId}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertThat(issueRepository.findById(issueId)).isEmpty();
        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId)).isEmpty();
        assertThat(cardRepository.findById(cardId)).isPresent();
    }

    @Test
    void anotherUserCannotRemoveRelation() throws Exception {
        String ownerToken = register("unlink-relation-owner", "unlink-relation-owner@example.com");
        String otherToken = register("unlink-relation-other", "unlink-relation-other@example.com");
        long issueId = createIssue(ownerToken, "私人作品");
        long cardId = createCard(ownerToken, "私人卡片");
        addCard(ownerToken, issueId, cardId);

        mockMvc.perform(delete("/api/issues/{issueId}/cards/{cardId}", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId)).isPresent();
    }

    @Test
    void missingCardIdIsRejected() throws Exception {
        String token = register("invalid-relation", "invalid-relation@example.com");
        long issueId = createIssue(token, "驗證作品");

        mockMvc.perform(post("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(issueCardRepository.count()).isZero();
    }

    @Test
    void statusTransitionSetsPreservesAndClearsUsedAtWithoutChangingCardGrowth() throws Exception {
        String token = register("status-relation-owner", "status-relation-owner@example.com");
        long issueId = createIssue(token, "狀態切換作品");
        long cardId = createCard(token, "狀態切換素材");
        addCard(token, issueId, cardId);

        JsonNode firstUsedResponse = updateStatus(token, issueId, cardId, "USED");
        String firstUsedAt = firstUsedResponse.get("usedAt").asText();
        assertThat(firstUsedResponse.get("status").asText()).isEqualTo("USED");
        assertThat(firstUsedAt).isNotBlank();
        java.time.ZonedDateTime persistedUsedAt = issueCardRepository
                .findByIssueIdAndCardId(issueId, cardId)
                .orElseThrow()
                .getUsedAt();

        JsonNode repeatedUsedResponse = updateStatus(token, issueId, cardId, "USED");
        assertThat(repeatedUsedResponse.get("usedAt").asText()).isNotBlank();
        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getUsedAt)
                .isEqualTo(persistedUsedAt);

        JsonNode candidateResponse = updateStatus(token, issueId, cardId, "CANDIDATE");
        assertThat(candidateResponse.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidateResponse.get("usedAt").isNull()).isTrue();

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(card -> card.getGrowthStatus())
                .isEqualTo(CardGrowthStatus.UNMARKED);
    }

    @Test
    void anotherUserCannotUpdateRelationStatus() throws Exception {
        String ownerToken = register("status-owner", "status-owner@example.com");
        String otherToken = register("status-other", "status-other@example.com");
        long issueId = createIssue(ownerToken, "私人狀態作品");
        long cardId = createCard(ownerToken, "私人狀態素材");
        addCard(ownerToken, issueId, cardId);

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/status", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new StatusRequest("USED"))))
                .andExpect(status().isForbidden());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getStatus)
                .isEqualTo(IssueCardStatus.CANDIDATE);
    }

    @Test
    void missingStatusIsRejectedWithoutChangingRelation() throws Exception {
        String token = register("invalid-status-owner", "invalid-status-owner@example.com");
        long issueId = createIssue(token, "驗證狀態作品");
        long cardId = createCard(token, "驗證狀態素材");
        addCard(token, issueId, cardId);

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/status", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getStatus)
                .isEqualTo(IssueCardStatus.CANDIDATE);
    }

    @Test
    void updateNoteNormalizesAndClearsRelationNote() throws Exception {
        String token = register("note-owner", "note-owner@example.com");
        long issueId = createIssue(token, "備註作品");
        long cardId = createCard(token, "備註素材");
        addCard(token, issueId, cardId);

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/note", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("  用於開場論點  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("用於開場論點"));

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getNote)
                .isEqualTo("用於開場論點");

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/note", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").doesNotExist());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getNote)
                .isNull();
    }

    @Test
    void updateNoteValidatesLengthAndRelationOwnership() throws Exception {
        String ownerToken = register("note-private-owner", "note-private-owner@example.com");
        String otherToken = register("note-private-other", "note-private-other@example.com");
        long issueId = createIssue(ownerToken, "私人備註作品");
        long cardId = createCard(ownerToken, "私人備註素材");
        addCard(ownerToken, issueId, cardId);

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/note", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("不應寫入"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/note", issueId, cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new NoteRequest("a".repeat(1001)))))
                .andExpect(status().isBadRequest());

        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId))
                .get()
                .extracting(IssueCard::getNote)
                .isNull();
    }

    @Test
    void issueCardListReturnsCardDisplayDataAndRelationStatus() throws Exception {
        String token = register("list-owner", "list-owner@example.com");
        long issueId = createIssue(token, "素材列表作品");
        long candidateCardId = createCard(token, "候選卡片", List.of("AI", "Java"));
        long usedCardId = createCard(token, "已使用卡片", List.of("創作"));
        addCard(token, issueId, candidateCardId);
        addCard(token, issueId, usedCardId);
        updateStatus(token, issueId, usedCardId, "USED");

        MvcResult candidateResult = mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .param("status", "CANDIDATE")
                .param("page", "0")
                .param("size", "10")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andReturn();

        JsonNode candidateResponse = objectMapper.readTree(candidateResult.getResponse().getContentAsString());
        JsonNode candidate = findRelation(candidateResponse.get("items"), candidateCardId);
        assertThat(candidate.get("cardTitle").asText()).isEqualTo("候選卡片");
        assertThat(candidate.get("cardType").asText()).isEqualTo("note");
        assertThat(candidate.get("cardGrowthStatus").asText()).isEqualTo("UNMARKED");
        assertThat(candidate.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidate.get("tags").get(0).asText()).isEqualTo("AI");
        assertThat(candidate.get("tags").get(1).asText()).isEqualTo("Java");

        MvcResult usedResult = mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .param("status", "USED")
                .param("page", "0")
                .param("size", "10")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andReturn();
        JsonNode usedResponse = objectMapper.readTree(usedResult.getResponse().getContentAsString());
        JsonNode used = findRelation(usedResponse.get("items"), usedCardId);
        assertThat(used.get("status").asText()).isEqualTo("USED");
        assertThat(used.get("usedAt").isNull()).isFalse();
    }

    @Test
    void issueCardListReturnsEmptyPageWhenIssueHasNoCards() throws Exception {
        String token = register("empty-list-owner", "empty-list-owner@example.com");
        long issueId = createIssue(token, "空素材作品");

        mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void issueCardListPaginatesWithinEachStatus() throws Exception {
        String token = register("paged-list-owner", "paged-list-owner@example.com");
        long issueId = createIssue(token, "分頁素材作品");
        addCard(token, issueId, createCard(token, "素材一"));
        addCard(token, issueId, createCard(token, "素材二"));
        addCard(token, issueId, createCard(token, "素材三"));

        mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .param("status", "CANDIDATE")
                .param("page", "0")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));

        mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .param("status", "CANDIDATE")
                .param("page", "1")
                .param("size", "2")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void anotherUserCannotReadIssueCardList() throws Exception {
        String ownerToken = register("private-list-owner", "private-list-owner@example.com");
        String otherToken = register("private-list-other", "private-list-other@example.com");
        long issueId = createIssue(ownerToken, "私人素材列表");
        long cardId = createCard(ownerToken, "私人素材");
        addCard(ownerToken, issueId, cardId);

        mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cardIssueListReturnsCandidateAndUsedRelations() throws Exception {
        String token = register("card-issue-owner", "card-issue-owner@example.com");
        long cardId = createCard(token, "可重複使用素材");
        long candidateIssueId = createIssue(token, "候選作品");
        long usedIssueId = createIssue(token, "已採用作品");
        addCard(token, candidateIssueId, cardId);
        addCard(token, usedIssueId, cardId);
        updateStatus(token, usedIssueId, cardId, "USED");

        MvcResult result = mockMvc.perform(get("/api/cards/{cardId}/issues", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode candidate = findCardIssue(response, candidateIssueId);
        assertThat(candidate.get("issueTitle").asText()).isEqualTo("候選作品");
        assertThat(candidate.get("issueStatus").asText()).isEqualTo("IDEA");
        assertThat(candidate.get("status").asText()).isEqualTo("CANDIDATE");
        assertThat(candidate.get("usedAt").isNull()).isTrue();

        JsonNode used = findCardIssue(response, usedIssueId);
        assertThat(used.get("issueTitle").asText()).isEqualTo("已採用作品");
        assertThat(used.get("status").asText()).isEqualTo("USED");
        assertThat(used.get("usedAt").isNull()).isFalse();
    }

    @Test
    void cardIssueListIsEmptyWithoutRelationsAndRejectsOtherUsers() throws Exception {
        String ownerToken = register("card-issue-private-owner", "card-issue-private-owner@example.com");
        String otherToken = register("card-issue-private-other", "card-issue-private-other@example.com");
        long cardId = createCard(ownerToken, "私人卡片");

        mockMvc.perform(get("/api/cards/{cardId}/issues", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/cards/{cardId}/issues", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void issueListReturnsCandidateAndUsedCountsPerIssueAndUser() throws Exception {
        String firstToken = register("count-first-owner", "count-first-owner@example.com");
        String secondToken = register("count-second-owner", "count-second-owner@example.com");
        long populatedIssueId = createIssue(
                firstToken,
                "有素材作品",
                "把候選素材整理成一篇文章",
                "https://example.com/issue");
        long emptyIssueId = createIssue(firstToken, "空素材作品");
        long otherIssueId = createIssue(secondToken, "其他使用者作品");
        long candidateCardId = createCard(firstToken, "候選計數素材");
        long usedCardId = createCard(firstToken, "使用計數素材");
        long otherCardId = createCard(secondToken, "其他使用者素材");
        addCard(firstToken, populatedIssueId, candidateCardId);
        addCard(firstToken, populatedIssueId, usedCardId);
        updateStatus(firstToken, populatedIssueId, usedCardId, "USED");
        addCard(secondToken, otherIssueId, otherCardId);
        updateStatus(secondToken, otherIssueId, otherCardId, "USED");

        MvcResult result = mockMvc.perform(get("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode populatedIssue = findIssue(response, populatedIssueId);
        assertThat(populatedIssue.get("description").asText()).isEqualTo("把候選素材整理成一篇文章");
        assertThat(populatedIssue.get("externalUrl").asText()).isEqualTo("https://example.com/issue");
        assertThat(populatedIssue.get("candidateCount").asLong()).isEqualTo(1);
        assertThat(populatedIssue.get("usedCount").asLong()).isEqualTo(1);

        JsonNode emptyIssue = findIssue(response, emptyIssueId);
        assertThat(emptyIssue.get("candidateCount").asLong()).isZero();
        assertThat(emptyIssue.get("usedCount").asLong()).isZero();
    }

    @Test
    void cardBoardsAndInteractionsKeepIssueRelationAndGrowthStatus() throws Exception {
        String token = register("regression-owner", "regression-owner@example.com");
        long issueId = createIssue(token, "回歸驗收作品");
        long cardId = createCard(token, "回歸驗收卡片");
        addCard(token, issueId, cardId);

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
                .andExpect(jsonPath("$.growthStatus").value("UNMARKED"));

        mockMvc.perform(put("/api/cards/{id}/snooze", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nextIntervalDays\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextShowAt").exists())
                .andExpect(jsonPath("$.growthStatus").value("UNMARKED"));

        mockMvc.perform(put("/api/cards/{id}/read", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openCount").value(1))
                .andExpect(jsonPath("$.growthStatus").value("UNMARKED"));

        mockMvc.perform(put("/api/cards/{id}/archive", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archivedStatus\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isArchived").value(true));

        assertCardListContains(token, "archived", cardId);
        assertCardListExcludes(token, "all", cardId);

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new IssueUpdateRequest(
                        "封存後仍保留素材",
                        null,
                        null,
                        "ARCHIVED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"));

        mockMvc.perform(get("/api/issues/{issueId}/cards", issueId)
                .param("status", "CANDIDATE")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].cardId").value(cardId))
                .andExpect(jsonPath("$.items[0].status").value("CANDIDATE"))
                .andExpect(jsonPath("$.items[0].cardGrowthStatus").value("UNMARKED"));

        assertThat(cardRepository.existsById(cardId)).isTrue();
        assertThat(issueCardRepository.findByIssueIdAndCardId(issueId, cardId)).isPresent();
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

    private long createIssue(String token, String title) throws Exception {
        return createIssue(token, title, null, null);
    }

    private long createIssue(String token, String title, String description, String externalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new IssueRequest(title, description, externalUrl))))
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

    private void addCard(String token, long issueId, long cardId) throws Exception {
        mockMvc.perform(post("/api/issues/{issueId}/cards", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(addCardJson(cardId, null)))
                .andExpect(status().isOk());
    }

    private JsonNode updateStatus(String token, long issueId, long cardId, String statusValue) throws Exception {
        MvcResult result = mockMvc.perform(put("/api/issues/{issueId}/cards/{cardId}/status", issueId, cardId)
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

    private JsonNode findIssue(JsonNode issues, long issueId) {
        for (JsonNode issue : issues) {
            if (issue.get("id").asLong() == issueId) {
                return issue;
            }
        }
        throw new AssertionError("找不到 id=" + issueId + " 的作品");
    }

    private JsonNode findCardIssue(JsonNode relations, long issueId) {
        for (JsonNode relation : relations) {
            if (relation.get("issueId").asLong() == issueId) {
                return relation;
            }
        }
        throw new AssertionError("找不到 issueId=" + issueId + " 的卡片作品關聯");
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

    private record IssueRequest(String title, String description, String externalUrl) {}

    private record IssueUpdateRequest(
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
