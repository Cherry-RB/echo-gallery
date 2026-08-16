package com.echogallery.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

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
import com.echogallery.work.WorkRepository;

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
    private WorkRepository workRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @BeforeEach
    void cleanDatabase() {
        workRepository.deleteAll();
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

        long growingCardId = createCard(ownerToken, "Growing card", new String[0]);
        long matureCardId = createCard(ownerToken, "Mature card", new String[0]);
        createCard(ownerToken, "Seed card", new String[0]);
        long archivedCardId = createCard(ownerToken, "Archived mature card", new String[0]);
        long otherCardId = createCard(otherToken, "Other user's mature card", new String[0]);

        updateCard(ownerToken, growingCardId, "Growing card", false, "GROWING");
        updateCard(ownerToken, matureCardId, "Mature card", false, "MATURE");
        updateCard(ownerToken, archivedCardId, "Archived mature card", true, "MATURE");
        updateCard(otherToken, otherCardId, "Other user's mature card", false, "MATURE");
        createWork(ownerToken, "Unfinished owner work");
        long completedWorkId = createWork(ownerToken, "Completed owner work");
        updateWork(ownerToken, completedWorkId, "Completed owner work", "DONE");
        createWork(otherToken, "Other work");

        mockMvc.perform(get("/api/sidebar/stats")
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCards").value(3))
                .andExpect(jsonPath("$.totalWorks").value(2))
                .andExpect(jsonPath("$.unfinishedWorks").value(1))
                .andExpect(jsonPath("$.todayEchoCards").value(0))
                .andExpect(jsonPath("$.highSnoozeCards").value(0))
                .andExpect(jsonPath("$.seedCards").value(1))
                .andExpect(jsonPath("$.growingCards").value(1))
                .andExpect(jsonPath("$.matureCards").value(1));
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

    private void updateCard(
            String token,
            long cardId,
            String title,
            boolean archived,
            String growthStatus) throws Exception {
        String body = objectMapper.writeValueAsString(new CardUpdatePayload(
                "note",
                title,
                new String[0],
                10,
                archived,
                growthStatus));

        mockMvc.perform(put("/api/cards/{id}", cardId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk());
    }

    private long createWork(String token, String title) throws Exception {
        String body = objectMapper.writeValueAsString(new WorkPayload(title, null, null));

        MvcResult result = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void updateWork(String token, long workId, String title, String workStatus) throws Exception {
        String body = objectMapper.writeValueAsString(new WorkUpdatePayload(title, null, null, workStatus));

        mockMvc.perform(put("/api/works/{id}", workId)
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
        return tags.get(0).get("id").asLong();
    }

    private String cardRequestJson(String title, String[] tags) throws Exception {
        return objectMapper.writeValueAsString(new CardPayload(
                "note",
                title,
                "測試原因",
                "測試摘要",
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
            String reason,
            String summary,
            String content,
            String[] tags,
            Integer intervalDays,
            Boolean isArchived
    ) {}

    private record CardUpdatePayload(
            String type,
            String title,
            String[] tags,
            Integer intervalDays,
            Boolean isArchived,
            String growthStatus
    ) {}

    private record WorkPayload(String title, String description, String externalUrl) {}

    private record WorkUpdatePayload(
            String title,
            String description,
            String externalUrl,
            String status
    ) {}
}
