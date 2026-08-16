package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echogallery.card.CardRepository;
import com.echogallery.card.CardGrowthStatus;
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
        MvcResult result = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkRequest(title, null, null))))
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

    private String addCardJson(long cardId, String note) throws Exception {
        return objectMapper.writeValueAsString(new AddCardRequest(cardId, note));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record WorkRequest(String title, String description, String externalUrl) {}

    private record AddCardRequest(Long cardId, String note) {}

    private record StatusRequest(String status) {}

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
