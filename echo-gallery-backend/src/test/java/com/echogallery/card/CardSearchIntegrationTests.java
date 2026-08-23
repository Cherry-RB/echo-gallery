package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import jakarta.persistence.EntityManagerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class CardSearchIntegrationTests extends IntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkCardRepository workCardRepository;
    @Autowired private WorkRepository workRepository;
    @Autowired private CardRepository cardRepository;
    @Autowired private TagRepository tagRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void cleanDatabase() {
        workCardRepository.deleteAll();
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void emptySearchReturnsOnlyOwnedActiveCardsAndSupportsArchiveFilter() throws Exception {
        String ownerToken = register("search-owner", "search-owner@example.com");
        String otherToken = register("search-other", "search-other@example.com");
        long activeId = createCard(ownerToken, "自己的未封存卡片", List.of());
        long archivedId = createCard(ownerToken, "自己的封存卡片", List.of());
        createCard(otherToken, "別人的卡片", List.of());
        archiveCard(ownerToken, archivedId);

        search(ownerToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(activeId));

        search(ownerToken, "archiveStatus", "ARCHIVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(archivedId));

        search(ownerToken, "archiveStatus", "ALL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinesExactIdTitleAndOwnershipWithoutFallingBackToNumericTitle() throws Exception {
        String ownerToken = register("id-owner", "id-owner@example.com");
        String otherToken = register("id-other", "id-other@example.com");
        long expectedId = createCard(ownerToken, "三國 Leadership", List.of());
        createCard(ownerToken, "標題包含 " + expectedId, List.of());
        long otherId = createCard(otherToken, "三國 Leadership", List.of());

        search(ownerToken, "id", String.valueOf(expectedId), "title", "LEADERSHIP")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(expectedId));

        search(ownerToken, "id", String.valueOf(expectedId), "title", "不符合")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        search(ownerToken, "id", String.valueOf(otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersSingleAndMultipleGrowthStatuses() throws Exception {
        String token = register("growth-owner", "growth-owner@example.com");
        long unmarkedId = createCard(token, "未標記", List.of());
        long seedId = createCard(token, "種子", List.of());
        long matureId = createCard(token, "成熟", List.of());
        updateGrowthStatus(token, seedId, "SEED");
        updateGrowthStatus(token, matureId, "MATURE");

        search(token, "growthStatuses", "SEED")
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(seedId));

        MvcResult result = search(token, "growthStatuses", "UNMARKED,MATURE,UNMARKED", "sortBy", "ID", "direction", "ASC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andReturn();
        assertThat(ids(result)).containsExactly(unmarkedId, matureId);
    }

    @Test
    void supportsTagOrAndWithoutDuplicatesAndNormalizesDuplicateIds() throws Exception {
        String token = register("tag-owner", "tag-owner@example.com");
        long bothId = createCard(token, "兩個標籤", List.of("Java", "Vue"));
        long javaId = createCard(token, "只有 Java", List.of("Java"));
        long javaTagId = tagId(bothId, "Java");
        long vueTagId = tagId(bothId, "Vue");

        search(token, "tagIds", javaTagId + "," + vueTagId, "tagMode", "OR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(2));

        search(token, "tagIds", javaTagId + "," + vueTagId, "tagMode", "AND")
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(bothId));

        search(token, "tagIds", javaTagId + "," + javaTagId, "tagMode", "AND")
                .andExpect(jsonPath("$.totalElements").value(2));

        search(token, "tagIds", javaTagId + ",999999", "tagMode", "AND")
                .andExpect(jsonPath("$.totalElements").value(0));
        assertThat(javaId).isPositive();
    }

    @Test
    void returnsCorrectTotalsAndStableAscendingAndDescendingPages() throws Exception {
        String token = register("page-owner", "page-owner@example.com");
        long firstId = createCard(token, "分頁素材", List.of());
        long secondId = createCard(token, "分頁素材", List.of());
        long thirdId = createCard(token, "分頁素材", List.of());

        MvcResult firstPage = search(token, "title", "分頁", "sortBy", "ID", "direction", "ASC", "page", "0", "size", "2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andReturn();
        MvcResult secondPage = search(token, "title", "分頁", "sortBy", "ID", "direction", "ASC", "page", "1", "size", "2")
                .andReturn();
        MvcResult descending = search(token, "title", "分頁", "sortBy", "ID", "direction", "DESC")
                .andReturn();

        assertThat(ids(firstPage)).containsExactly(firstId, secondId);
        assertThat(ids(secondPage)).containsExactly(thirdId);
        assertThat(ids(descending)).containsExactly(thirdId, secondId, firstId);
    }

    @Test
    void supportsCreatedAndUpdatedSortFieldsInBothDirections() throws Exception {
        String token = register("time-sort-owner", "time-sort-owner@example.com");
        long firstId = createCard(token, "時間排序一", List.of());
        long secondId = createCard(token, "時間排序二", List.of());

        for (String sortBy : List.of("CREATED_AT", "UPDATED_AT")) {
            assertThat(ids(search(token, "sortBy", sortBy, "direction", "ASC").andReturn()))
                    .containsExactly(firstId, secondId);
            assertThat(ids(search(token, "sortBy", sortBy, "direction", "DESC").andReturn()))
                    .containsExactly(secondId, firstId);
        }
    }

    @Test
    void placesNullNextShowAtLastInBothDirections() throws Exception {
        String token = register("null-owner", "null-owner@example.com");
        long scheduledId = createCard(token, "有排程", List.of());
        long nullId = createCard(token, "無排程", List.of());
        Card nullCard = cardRepository.findById(nullId).orElseThrow();
        nullCard.setNextShowAt(null);
        cardRepository.saveAndFlush(nullCard);

        assertThat(ids(search(token, "sortBy", "NEXT_SHOW_AT", "direction", "ASC").andReturn()))
                .containsExactly(scheduledId, nullId);
        assertThat(ids(search(token, "sortBy", "NEXT_SHOW_AT", "direction", "DESC").andReturn()))
                .containsExactly(scheduledId, nullId);
    }

    @Test
    void rejectsIllegalParametersAndPageSizeOverLimit() throws Exception {
        String token = register("validation-owner", "validation-owner@example.com");
        search(token, "id", "-1").andExpect(status().isBadRequest());
        search(token, "title", "a".repeat(256)).andExpect(status().isBadRequest());
        search(token, "sortBy", "TITLE").andExpect(status().isBadRequest());
        search(token, "direction", "SIDEWAYS").andExpect(status().isBadRequest());
        search(token, "page", "-1").andExpect(status().isBadRequest());
        search(token, "size", "101").andExpect(status().isBadRequest());
        search(token, "size", "100").andExpect(status().isOk());
    }

    @Test
    void searchDoesNotUpdateReviewOrInteractionFields() throws Exception {
        String token = register("readonly-owner", "readonly-owner@example.com");
        long cardId = createCard(token, "唯讀查詢", List.of("唯讀"));
        Card before = cardRepository.findById(cardId).orElseThrow();
        ZonedDateTime nextShowAt = before.getNextShowAt();
        ZonedDateTime lastOpenAt = before.getLastOpenAt();
        ZonedDateTime lastInteractionAt = before.getLastInteractionAt();
        int openCount = before.getOpenCount();

        search(token, "id", String.valueOf(cardId)).andExpect(status().isOk());

        Card after = cardRepository.findById(cardId).orElseThrow();
        assertThat(after.getNextShowAt()).isEqualTo(nextShowAt);
        assertThat(after.getLastOpenAt()).isEqualTo(lastOpenAt);
        assertThat(after.getLastInteractionAt()).isEqualTo(lastInteractionAt);
        assertThat(after.getOpenCount()).isEqualTo(openCount);
    }

    @Test
    void loadsPageTagsWithBoundedQueryCount() throws Exception {
        String token = register("query-owner", "query-owner@example.com");
        for (int index = 0; index < 25; index++) {
            createCard(token, "查詢數 " + index, List.of("共同標籤", "標籤" + index));
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        search(token, "title", "查詢數", "size", "20").andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(5);
    }

    private org.springframework.test.web.servlet.ResultActions search(String token, String... parameters) throws Exception {
        var request = get("/api/cards/search").header(HttpHeaders.AUTHORIZATION, bearer(token));
        for (int index = 0; index < parameters.length; index += 2) {
            request.param(parameters[index], parameters[index + 1]);
        }
        return mockMvc.perform(request);
    }

    private List<Long> ids(MvcResult result) throws Exception {
        JsonNode content = objectMapper.readTree(result.getResponse().getContentAsString()).get("content");
        return java.util.stream.StreamSupport.stream(content.spliterator(), false)
                .map(node -> node.get("id").asLong())
                .toList();
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RegistrationRequest(username, email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createCard(String token, String title, List<String> tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CardRequest(
                        "note", title, null, null, null, null, null, tags, 10))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long tagId(long cardId, String name) {
        Long userId = cardRepository.findById(cardId).orElseThrow().getUser().getId();
        return tagRepository.findByUserIdAndName(userId, name).orElseThrow().getId();
    }

    private void updateGrowthStatus(String token, long cardId, String statusValue) throws Exception {
        mockMvc.perform(put("/api/cards/{id}/growth-status", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"growthStatus\":\"" + statusValue + "\"}"))
                .andExpect(status().isOk());
    }

    private void archiveCard(String token, long cardId) throws Exception {
        mockMvc.perform(put("/api/cards/{id}/archive", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"archivedStatus\":true}"))
                .andExpect(status().isOk());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}
    private record CardRequest(
            String type, String title, String coverImageUrl, String url, String summary,
            String content, String reason, List<String> tags, Integer intervalDays) {}
}
