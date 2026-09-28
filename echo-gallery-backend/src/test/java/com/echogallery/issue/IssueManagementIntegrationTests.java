package com.echogallery.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.echogallery.card.CardRepository;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class IssueManagementIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IssueRepository issueRepository;

    @Autowired
    private IssueUpdateRepository progressUpdateRepository;

    @Autowired
    private CardRepository cardRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        issueRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createIssueDefaultsToIdeaAndCanBeRead() throws Exception {
        String token = register("issue-creator", "issue-creator@example.com");
        long issueId = createIssue(token, " 第一篇作品 ", "初稿說明", " https://example.com/draft ");

        mockMvc.perform(get("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(issueId))
                .andExpect(jsonPath("$.title").value("第一篇作品"))
                .andExpect(jsonPath("$.objective").doesNotExist())
                .andExpect(jsonPath("$.description").value("初稿說明"))
                .andExpect(jsonPath("$.currentAssessment").doesNotExist())
                .andExpect(jsonPath("$.outcomeCriteria").doesNotExist())
                .andExpect(jsonPath("$.status").value("IDEA"))
                .andExpect(jsonPath("$.externalUrl").value("https://example.com/draft"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void createAndUpdateIssuePreservesStructuredIssueBoardFields() throws Exception {
        String token = register("issue-board", "issue-board@example.com");
        MvcResult createResult = mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateIssuePayload(
                        "Echo Gallery 下一階段",
                        "釐清回流內容如何進入判斷",
                        "卡片目前以外部收藏素材為主",
                        "目前缺少素材與決議之間的推演層",
                        "完成兩個真實議題的使用觀察",
                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objective").value("釐清回流內容如何進入判斷"))
                .andExpect(jsonPath("$.description").value("卡片目前以外部收藏素材為主"))
                .andExpect(jsonPath("$.currentAssessment").value("目前缺少素材與決議之間的推演層"))
                .andExpect(jsonPath("$.outcomeCriteria").value("完成兩個真實議題的使用觀察"))
                .andReturn();

        long issueId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateIssuePayload(
                        "Echo Gallery 下一階段",
                        "驗證議題盤面是否值得保留",
                        "舊背景仍需完整保留",
                        "已具備最小可用的結構化盤面",
                        "使用四週後重新評估",
                        "ACTIVE",
                        null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objective").value("驗證議題盤面是否值得保留"))
                .andExpect(jsonPath("$.description").value("舊背景仍需完整保留"))
                .andExpect(jsonPath("$.currentAssessment").value("已具備最小可用的結構化盤面"))
                .andExpect(jsonPath("$.outcomeCriteria").value("使用四週後重新評估"));

        mockMvc.perform(get("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objective").value("驗證議題盤面是否值得保留"))
                .andExpect(jsonPath("$[0].description").value("舊背景仍需完整保留"))
                .andExpect(jsonPath("$[0].currentAssessment").value("已具備最小可用的結構化盤面"))
                .andExpect(jsonPath("$[0].outcomeCriteria").value("使用四週後重新評估"));
    }

    @Test
    void listContainsOnlyCurrentUsersIssues() throws Exception {
        String firstToken = register("first-issueer", "first-issueer@example.com");
        String secondToken = register("second-issueer", "second-issueer@example.com");
        createIssue(firstToken, "我的作品", null, null);
        createIssue(secondToken, "別人的作品", null, null);

        mockMvc.perform(get("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("我的作品"));
    }

    @Test
    void doneSetsCompletedAtAndReturningToActiveClearsIt() throws Exception {
        String token = register("status-issueer", "status-issueer@example.com");
        long issueId = createIssue(token, "狀態作品", null, null);

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueUpdateJson("完成作品", "DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueUpdateJson("繼續修改", "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueUpdateJson("封存作品", "ARCHIVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void anotherUserCannotReadOrUpdateIssue() throws Exception {
        String ownerToken = register("issue-owner", "issue-owner@example.com");
        String otherToken = register("issue-other", "issue-other@example.com");
        long issueId = createIssue(ownerToken, "私人作品", null, null);

        mockMvc.perform(get("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueUpdateJson("越權修改", "DRAFT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIssueRemovesItsProgressUpdatesButKeepsOtherIssues() throws Exception {
        String ownerToken = register("delete-issue-owner", "delete-issue-owner@example.com");
        long deletedIssueId = createIssue(ownerToken, "待刪除議題", null, null);
        long retainedIssueId = createIssue(ownerToken, "保留議題", null, null);

        mockMvc.perform(post("/api/issues/{issueId}/updates", deletedIssueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"changeSummary\":\"待移除的近況\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/issues/{id}", deletedIssueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken)))
                .andExpect(status().isNoContent());

        assertThat(issueRepository.findById(deletedIssueId)).isEmpty();
        assertThat(progressUpdateRepository.findByIssueIdOrderByCreatedAtDescIdDesc(
                deletedIssueId,
                org.springframework.data.domain.PageRequest.of(0, 1)).getContent()).isEmpty();
        assertThat(issueRepository.findById(retainedIssueId)).isPresent();
    }

    @Test
    void anotherUserCannotDeleteIssue() throws Exception {
        String ownerToken = register("delete-owner", "delete-owner@example.com");
        String otherToken = register("delete-other", "delete-other@example.com");
        long issueId = createIssue(ownerToken, "私人議題", null, null);

        mockMvc.perform(delete("/api/issues/{id}", issueId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        assertThat(issueRepository.findById(issueId)).isPresent();
    }

    @Test
    void invalidTitleAndExternalUrlAreRejected() throws Exception {
        String token = register("validation-issueer", "validation-issueer@example.com");

        mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueCreateJson("   ", null, null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueCreateJson("無效連結", null, "ftp://example.com/file")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateIssuePayload(
                        "內容過長",
                        "a".repeat(50001),
                        null,
                        null,
                        null,
                        null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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

    private long createIssue(String token, String title, String description, String externalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/issues")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(issueCreateJson(title, description, externalUrl)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String issueCreateJson(String title, String description, String externalUrl) throws Exception {
        return objectMapper.writeValueAsString(
                new CreateIssuePayload(title, null, description, null, null, externalUrl));
    }

    private String issueUpdateJson(String title, String status) throws Exception {
        return objectMapper.writeValueAsString(
                new UpdateIssuePayload(title, null, null, null, null, status, null));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record CreateIssuePayload(
            String title,
            String objective,
            String description,
            String currentAssessment,
            String outcomeCriteria,
            String externalUrl) {}

    private record UpdateIssuePayload(
            String title,
            String objective,
            String description,
            String currentAssessment,
            String outcomeCriteria,
            String status,
            String externalUrl) {}
}
