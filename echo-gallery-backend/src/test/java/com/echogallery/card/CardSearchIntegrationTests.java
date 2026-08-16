package com.echogallery.card;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;
import com.echogallery.work.WorkCardRepository;
import com.echogallery.work.WorkRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class CardSearchIntegrationTests extends IntegrationTestBase {

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
    void searchesOwnedActiveCardsByPartialTitleIgnoringCase() throws Exception {
        String ownerToken = register("search-owner", "search-owner@example.com");
        String otherToken = register("search-other", "search-other@example.com");
        long expectedId = createCard(ownerToken, "三國 Leadership 分析");
        createCard(ownerToken, "無關卡片");
        createCard(otherToken, "三國其他使用者資料");
        long archivedId = createCard(ownerToken, "三國已封存資料");
        archiveCard(ownerToken, archivedId);

        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .param("keyword", "LEADERSHIP")
                .param("pageNumber", "1")
                .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(expectedId))
                .andExpect(jsonPath("$[0].title").value("三國 Leadership 分析"));

        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .param("keyword", "三國"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(expectedId));
    }

    @Test
    void searchesByPlainOrHashPrefixedCardIdWithoutLeakingOtherUsersCards() throws Exception {
        String ownerToken = register("id-owner", "id-owner@example.com");
        String otherToken = register("id-other", "id-other@example.com");
        long ownerCardId = createCard(ownerToken, "自己的卡片");
        long otherCardId = createCard(otherToken, "別人的卡片");

        assertSingleSearchResult(ownerToken, String.valueOf(ownerCardId), ownerCardId);
        assertSingleSearchResult(ownerToken, "#" + ownerCardId, ownerCardId);

        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .param("keyword", String.valueOf(otherCardId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void rejectsBlankOrOverlongSearchKeyword() throws Exception {
        String token = register("validation-owner", "validation-owner@example.com");

        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("keyword", "   "))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("keyword", "a".repeat(256)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void paginatesSearchResultsWithStableOrdering() throws Exception {
        String token = register("page-owner", "page-owner@example.com");
        long firstId = createCard(token, "分頁素材一");
        long secondId = createCard(token, "分頁素材二");
        long thirdId = createCard(token, "分頁素材三");

        MvcResult firstPageResult = mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("keyword", "分頁素材")
                .param("pageNumber", "1")
                .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andReturn();

        MvcResult secondPageResult = mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("keyword", "分頁素材")
                .param("pageNumber", "2")
                .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();

        JsonNode firstPage = objectMapper.readTree(firstPageResult.getResponse().getContentAsString());
        JsonNode secondPage = objectMapper.readTree(secondPageResult.getResponse().getContentAsString());
        assertThat(firstPage.get(0).get("id").asLong()).isEqualTo(thirdId);
        assertThat(firstPage.get(1).get("id").asLong()).isEqualTo(secondId);
        assertThat(secondPage.get(0).get("id").asLong()).isEqualTo(firstId);
    }

    private void assertSingleSearchResult(String token, String keyword, long expectedId) throws Exception {
        mockMvc.perform(get("/api/cards/search")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .param("keyword", keyword))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(expectedId));
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

    private long createCard(String token, String title) throws Exception {
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
                        List.of(),
                        10))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
