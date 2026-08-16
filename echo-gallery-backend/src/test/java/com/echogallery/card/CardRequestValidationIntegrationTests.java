package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import com.echogallery.work.WorkRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class CardRequestValidationIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WorkRepository workRepository;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
        token = register();
    }

    @Test
    void createRejectsInvalidIntervalWithoutWritingData() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("intervalDays", 0);

        performCreate(request).andExpect(status().isBadRequest());

        assertThat(cardRepository.count()).isZero();
        assertThat(tagRepository.count()).isZero();
    }

    @Test
    void createRejectsIntervalAboveMaximum() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("intervalDays", 366);

        performCreate(request).andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingAndOverlongRequiredFields() throws Exception {
        Map<String, Object> missingTitleRequest = validRequest();
        missingTitleRequest.remove("title");
        performCreate(missingTitleRequest).andExpect(status().isBadRequest());

        Map<String, Object> overlongTitleRequest = validRequest();
        overlongTitleRequest.put("title", "a".repeat(256));
        performCreate(overlongTitleRequest).andExpect(status().isBadRequest());

        assertThat(cardRepository.count()).isZero();
    }

    @Test
    void createAllowsNullIntervalForFuturePauseRecurrenceSemantics() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("intervalDays", null);

        performCreate(request).andExpect(status().isOk());
    }

    @Test
    void createRejectsLinkWithoutUrl() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("type", "link");
        request.put("url", null);

        performCreate(request).andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsUnsupportedUrlScheme() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("type", "link");
        request.put("url", "javascript:alert(1)");

        performCreate(request).andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsInvalidCoverImageUrl() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("coverImageUrl", "not a url");

        performCreate(request).andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMoreThanTenTagsEvenWhenTagsRepeat() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("tags", List.of(
                "tag", "tag", "tag", "tag", "tag", "tag",
                "tag", "tag", "tag", "tag", "tag"));

        performCreate(request).andExpect(status().isBadRequest());

        assertThat(cardRepository.count()).isZero();
        assertThat(tagRepository.count()).isZero();
    }

    @Test
    void createRejectsTagsThatRepeatAfterTrimming() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("tags", List.of("Java", " Java "));

        performCreate(request).andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsBlankNullAndOverlongTags() throws Exception {
        Map<String, Object> blankTagRequest = validRequest();
        blankTagRequest.put("tags", List.of("   "));
        performCreate(blankTagRequest).andExpect(status().isBadRequest());

        Map<String, Object> nullTagRequest = validRequest();
        java.util.ArrayList<String> tagsWithNull = new java.util.ArrayList<>();
        tagsWithNull.add(null);
        nullTagRequest.put("tags", tagsWithNull);
        performCreate(nullTagRequest).andExpect(status().isBadRequest());

        Map<String, Object> overlongTagRequest = validRequest();
        overlongTagRequest.put("tags", List.of("a".repeat(51)));
        performCreate(overlongTagRequest).andExpect(status().isBadRequest());

        assertThat(cardRepository.count()).isZero();
        assertThat(tagRepository.count()).isZero();
    }

    @Test
    void createTreatsTagCaseAsSignificant() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("tags", List.of("Java", "java"));

        performCreate(request).andExpect(status().isOk());

        assertThat(tagRepository.count()).isEqualTo(2);
    }

    @Test
    void createAcceptsExactlyTenDistinctTags() throws Exception {
        Map<String, Object> request = validRequest();
        request.put("tags", List.of(
                "tag-1", "tag-2", "tag-3", "tag-4", "tag-5",
                "tag-6", "tag-7", "tag-8", "tag-9", "tag-10"));

        performCreate(request).andExpect(status().isOk());

        assertThat(tagRepository.count()).isEqualTo(10);
    }

    @Test
    void createReturnsSeedGrowthStatusByDefault() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(createResult.getResponse().getContentAsString());

        assertThat(response.get("growthStatus").asText()).isEqualTo("SEED");
    }

    @Test
    void getCardDetailBindsPathVariable() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void updateChangesAndReturnsGrowthStatus() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("isArchived", false);
        updateRequest.put("growthStatus", "GROWING");

        MvcResult updateResult = mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(updateResult.getResponse().getContentAsString());
        assertThat(response.get("growthStatus").asText()).isEqualTo("GROWING");
        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.GROWING);
    }

    @Test
    void cardListReturnsGrowthStatus() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("isArchived", false);
        updateRequest.put("growthStatus", "MATURE");
        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        MvcResult listResult = mockMvc.perform(post("/api/cards/list")
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "pageNumber", 1,
                                "pageSize", 15,
                                "boardType", "all"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(listResult.getResponse().getContentAsString());
        assertThat(response.get(0).get("growthStatus").asText()).isEqualTo("MATURE");
    }

    @Test
    void updateWithoutGrowthStatusKeepsExistingValue() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("isArchived", false);

        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk());

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.SEED);
    }

    @Test
    void updateRejectsUnknownGrowthStatus() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("isArchived", false);
        updateRequest.put("growthStatus", "COMPLETED");

        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(cardRepository.findById(cardId))
                .get()
                .extracting(Card::getGrowthStatus)
                .isEqualTo(CardGrowthStatus.SEED);
    }

    @Test
    void updateRejectsMissingArchivedStatusWithoutChangingCard() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("title", "修改後標題");
        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(cardRepository.findById(cardId)).get().extracting(Card::getTitle).isEqualTo("有效標題");
    }

    @Test
    void updateRejectsExplicitNullArchivedStatusWithoutChangingCard() throws Exception {
        MvcResult createResult = performCreate(validRequest())
                .andExpect(status().isOk())
                .andReturn();
        long cardId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        Map<String, Object> updateRequest = validRequest();
        updateRequest.put("title", "修改後標題");
        updateRequest.put("isArchived", null);
        mockMvc.perform(put("/api/cards/{id}", cardId)
                        .header(HttpHeaders.AUTHORIZATION, bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isBadRequest());

        assertThat(cardRepository.findById(cardId)).get().extracting(Card::getTitle).isEqualTo("有效標題");
    }

    private org.springframework.test.web.servlet.ResultActions performCreate(Map<String, Object> request) throws Exception {
        return mockMvc.perform(post("/api/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private Map<String, Object> validRequest() {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("type", "note");
        request.put("title", "有效標題");
        request.put("coverImageUrl", "https://example.com/cover.jpg");
        request.put("url", "https://example.com/source");
        request.put("summary", "簡介");
        request.put("content", "內容");
        request.put("reason", "推薦原因");
        request.put("tags", List.of("Java"));
        request.put("intervalDays", 10);
        return request;
    }

    private String register() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "validation-user",
                "email", "validation@example.com",
                "password", "password123"));
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("token").asText();
    }

    private String bearer() {
        return "Bearer " + token;
    }
}
