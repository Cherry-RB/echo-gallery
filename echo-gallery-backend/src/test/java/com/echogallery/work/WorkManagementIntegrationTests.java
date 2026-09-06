package com.echogallery.work;

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
class WorkManagementIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createWorkDefaultsToIdeaAndCanBeRead() throws Exception {
        String token = register("work-creator", "work-creator@example.com");
        long workId = createWork(token, " 第一篇作品 ", "初稿說明", " https://example.com/draft ");

        mockMvc.perform(get("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(workId))
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
    void createAndUpdateWorkPreservesStructuredIssueBoardFields() throws Exception {
        String token = register("issue-board", "issue-board@example.com");
        MvcResult createResult = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkPayload(
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

        long workId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UpdateWorkPayload(
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

        mockMvc.perform(get("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].objective").value("驗證議題盤面是否值得保留"))
                .andExpect(jsonPath("$[0].description").value("舊背景仍需完整保留"))
                .andExpect(jsonPath("$[0].currentAssessment").value("已具備最小可用的結構化盤面"))
                .andExpect(jsonPath("$[0].outcomeCriteria").value("使用四週後重新評估"));
    }

    @Test
    void listContainsOnlyCurrentUsersWorks() throws Exception {
        String firstToken = register("first-worker", "first-worker@example.com");
        String secondToken = register("second-worker", "second-worker@example.com");
        createWork(firstToken, "我的作品", null, null);
        createWork(secondToken, "別人的作品", null, null);

        mockMvc.perform(get("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("我的作品"));
    }

    @Test
    void doneSetsCompletedAtAndReturningToActiveClearsIt() throws Exception {
        String token = register("status-worker", "status-worker@example.com");
        long workId = createWork(token, "狀態作品", null, null);

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workUpdateJson("完成作品", "DONE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.completedAt").exists());

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workUpdateJson("繼續修改", "ACTIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workUpdateJson("封存作品", "ARCHIVED")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.completedAt").doesNotExist());
    }

    @Test
    void anotherUserCannotReadOrUpdateWork() throws Exception {
        String ownerToken = register("work-owner", "work-owner@example.com");
        String otherToken = register("work-other", "work-other@example.com");
        long workId = createWork(ownerToken, "私人作品", null, null);

        mockMvc.perform(get("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/works/{id}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workUpdateJson("越權修改", "DRAFT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidTitleAndExternalUrlAreRejected() throws Exception {
        String token = register("validation-worker", "validation-worker@example.com");

        mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workCreateJson("   ", null, null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workCreateJson("無效連結", null, "ftp://example.com/file")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CreateWorkPayload(
                        "內容過長",
                        "a".repeat(50001),
                        null,
                        null,
                        null,
                        null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/works")
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

    private long createWork(String token, String title, String description, String externalUrl) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(workCreateJson(title, description, externalUrl)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String workCreateJson(String title, String description, String externalUrl) throws Exception {
        return objectMapper.writeValueAsString(
                new CreateWorkPayload(title, null, description, null, null, externalUrl));
    }

    private String workUpdateJson(String title, String status) throws Exception {
        return objectMapper.writeValueAsString(
                new UpdateWorkPayload(title, null, null, null, null, status, null));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record RegistrationRequest(String username, String email, String password) {}

    private record CreateWorkPayload(
            String title,
            String objective,
            String description,
            String currentAssessment,
            String outcomeCriteria,
            String externalUrl) {}

    private record UpdateWorkPayload(
            String title,
            String objective,
            String description,
            String currentAssessment,
            String outcomeCriteria,
            String status,
            String externalUrl) {}
}
