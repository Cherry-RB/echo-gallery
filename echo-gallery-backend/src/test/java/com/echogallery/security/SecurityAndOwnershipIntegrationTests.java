package com.echogallery.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;
import com.echogallery.issue.IssueRepository;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class SecurityAndOwnershipIntegrationTests extends IntegrationTestBase {

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
    private IssueRepository issueRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void cleanDatabase() {
        issueRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/sidebar/stats"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredFrontendAndRejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));

        mockMvc.perform(options("/api/auth/login")
                .header(HttpHeaders.ORIGIN, "https://not-allowed.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void malformedTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/sidebar/stats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-valid-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenReturnsUnauthorized() throws Exception {
        register("expired-user", "expired@example.com");

        long now = System.currentTimeMillis();
        SecretKey signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("expired@example.com")
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(signingKey)
                .compact();

        mockMvc.perform(get("/api/sidebar/stats")
                .header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenCanAccessProtectedEndpoint() throws Exception {
        String token = register("valid-user", "valid@example.com");

        mockMvc.perform(get("/api/sidebar/stats")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void sidebarStatsCountOnlyOwnedAndActiveCards() throws Exception {
        String ownerToken = register("stats-owner", "stats-owner@example.com");
        String otherToken = register("stats-other", "stats-other@example.com");

        createCard(ownerToken, "First active card", new String[0]);
        createCard(ownerToken, "Second active card", new String[0]);
        createCard(ownerToken, "Third active card", new String[0]);
        createCard(ownerToken, "Archived card", new String[0]);
        createCard(otherToken, "Other user's card", new String[0]);

        createIssue(ownerToken, "Unfinished owner issue");
        long completedIssueId = createIssue(ownerToken, "Completed owner issue");
        updateIssue(ownerToken, completedIssueId, "Completed owner issue", "DONE");
        createIssue(otherToken, "Other issue");

        mockMvc.perform(get("/api/sidebar/stats")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(4))
                .andExpect(jsonPath("$.totalIssues").value(2))
                .andExpect(jsonPath("$.unfinishedIssues").value(1))
                .andExpect(jsonPath("$.todayEchoCards").doesNotExist())
                .andExpect(jsonPath("$.highSnoozeCards").value(0));
    }

    @Test
    void userCannotReadUpdateOrDeleteAnotherUsersCard() throws Exception {
        String ownerToken = register("card-owner", "card-owner@example.com");
        String otherToken = register("card-other", "card-other@example.com");
        long cardId = createCard(ownerToken, "Owner card", new String[] { "private-tag" });

        mockMvc.perform(get("/api/cards/{id}", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/cards/{id}", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardRequestJson("Changed by another user", new String[] { "private-tag" })))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/cards/{id}", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void userCannotUpdateOrDeleteAnotherUsersTag() throws Exception {
        String ownerToken = register("tag-owner", "tag-owner@example.com");
        String otherToken = register("tag-other", "tag-other@example.com");
        createCard(ownerToken, "Tagged card", new String[] { "owner-only" });
        long tagId = firstTagId(ownerToken);

        mockMvc.perform(put("/api/tags/{id}", tagId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"unauthorized-change\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/tags/{id}", tagId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void tagListPaginatesAndSearchesOnlyCurrentUsersActiveTags() throws Exception {
        String token = register("tag-page-owner", "tag-page-owner@example.com");
        String otherToken = register("tag-page-other", "tag-page-other@example.com");
        createCard(token, "Alpha card", new String[] { "alpha" });
        createCard(token, "Beta card", new String[] { "beta" });
        createCard(token, "Gamma card", new String[] { "gamma" });
        createCard(otherToken, "Private card", new String[] { "alpha-private" });

        mockMvc.perform(get("/api/tags/list")
                .param("page", "0")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3));

        mockMvc.perform(get("/api/tags/list")
                .param("keyword", "alp")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].name").value("alpha"));
    }

    @Test
    void randomCardsWrapOnceAndReturnEveryActiveCardWithoutDuplicates() throws Exception {
        String token = register("random-owner", "random-owner@example.com");
        for (int index = 1; index <= 7; index++) {
            createCard(token, "Random card " + index, new String[0]);
        }

        JsonNode page = randomCards(token, Map.of("pageSize", 3));
        Set<Long> seenCardIds = new HashSet<>();
        int requestCount = 1;

        while (true) {
            for (JsonNode card : page.get("content")) {
                assertTrue(seenCardIds.add(card.get("id").asLong()), "同一輪隨機瀏覽不應出現重複卡片");
            }
            if (!page.get("hasMore").asBoolean()) {
                break;
            }

            assertTrue(requestCount++ < 5, "隨機 cursor 應在有限次請求內結束");
            page = randomCards(token, Map.of(
                    "pageSize", 3,
                    "startId", page.get("startId").asLong(),
                    "cursorId", page.get("cursorId").asLong()));
        }

        assertEquals(7, seenCardIds.size());
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

    private long createCard(String token, String title, String[] tags) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cardRequestJson(title, tags)))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createIssue(String token, String title) throws Exception {
        String body = objectMapper.writeValueAsString(new IssuePayload(title, null, null));

        MvcResult result = mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void updateIssue(String token, long issueId, String title, String issueStatus) throws Exception {
        String body = objectMapper.writeValueAsString(new IssueUpdatePayload(title, null, null, issueStatus));

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    private long firstTagId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tags/list")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode tags = objectMapper.readTree(result.getResponse().getContentAsString());
        return tags.get("content").get(0).get("id").asLong();
    }

    private JsonNode randomCards(String token, Map<String, Object> request) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/cards/random")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String cardRequestJson(String title, String[] tags) throws Exception {
        return objectMapper.writeValueAsString(new CardPayload(
                "note",
                title,
                "測試原因\n\n測試摘要",
                "測試內容",
                tags,
                10,
                false
        ));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record CardPayload(
            String type,
            String title,
            String cardNote,
            String content,
            String[] tags,
            Integer intervalDays,
            Boolean isArchived
    ) {}

    private record IssuePayload(String title, String description, String externalUrl) {}

    private record IssueUpdatePayload(
            String title,
            String description,
            String externalUrl,
            String status
    ) {}
}
