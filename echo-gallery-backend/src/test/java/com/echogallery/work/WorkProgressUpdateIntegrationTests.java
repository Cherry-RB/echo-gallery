package com.echogallery.work;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

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

import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class WorkProgressUpdateIntegrationTests extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WorkProgressUpdateRepository updateRepository;

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
        updateRepository.deleteAll();
        workRepository.deleteAll();
        cardRepository.deleteAll();
        tagRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void createListUpdateAndDeleteProgressUpdatesWithoutChangingWorkAssessment() throws Exception {
        String token = register("update-owner", "update-owner@example.com");
        long workId = createWork(token, "轉職方向探索", "整體研判維持不變");

        long firstUpdateId = createUpdate(
                token,
                workId,
                "取得第一輪市場回饋",
                "真正未知是市場如何評價目前能力",
                "投遞第二家公司");
        long secondUpdateId = createUpdate(
                token,
                workId,
                "第二家公司邀請面試",
                "目前方向得到初步驗證",
                "準備第二輪面試");

        mockMvc.perform(get("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(secondUpdateId))
                .andExpect(jsonPath("$.items[1].id").value(firstUpdateId))
                .andExpect(jsonPath("$.items[1].workTitle").value("轉職方向探索"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.hasNext").value(false));

        mockMvc.perform(get("/api/works/{workId}/updates", workId)
                .param("page", "0")
                .param("size", "1")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(secondUpdateId))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/works/{workId}/updates", workId)
                .param("page", "-1")
                .param("size", "999")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));

        mockMvc.perform(get("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestProgressAt").exists())
                .andExpect(jsonPath("$[0].latestProgressChangeSummary").value("第二家公司邀請面試"))
                .andExpect(jsonPath("$[0].latestProgressAssessment").value("目前方向得到初步驗證"))
                .andExpect(jsonPath("$[0].latestProgressNextStep").value("準備第二輪面試"));

        mockMvc.perform(put("/api/works/{workId}/updates/{updateId}", workId, firstUpdateId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("取得兩次市場回饋", "方向已逐漸清楚", "準備面試")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeSummary").value("取得兩次市場回饋"))
                .andExpect(jsonPath("$.assessment").value("方向已逐漸清楚"))
                .andExpect(jsonPath("$.nextStep").value("準備面試"));

        mockMvc.perform(get("/api/works/{workId}", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentAssessment").value("整體研判維持不變"));

        mockMvc.perform(delete("/api/works/{workId}/updates/{updateId}", workId, secondUpdateId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(firstUpdateId))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void recentUpdatesContainOnlyCurrentUsersData() throws Exception {
        String firstToken = register("recent-first", "recent-first@example.com");
        String secondToken = register("recent-second", "recent-second@example.com");
        long firstWorkId = createWork(firstToken, "我的議題", null);
        long secondWorkId = createWork(secondToken, "別人的議題", null);
        createUpdate(firstToken, firstWorkId, "我的最新進展", null, null);
        createUpdate(secondToken, secondWorkId, "別人的最新進展", null, null);

        mockMvc.perform(get("/api/work-updates/recent")
                .header(HttpHeaders.AUTHORIZATION, bearer(firstToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].workTitle").value("我的議題"))
                .andExpect(jsonPath("$[0].changeSummary").value("我的最新進展"));
    }

    @Test
    void anotherUserCannotReadOrMutateUpdates() throws Exception {
        String ownerToken = register("private-owner", "private-owner@example.com");
        String otherToken = register("private-other", "private-other@example.com");
        long workId = createWork(ownerToken, "私人議題", null);
        long updateId = createUpdate(ownerToken, workId, "私人進展", null, null);

        mockMvc.perform(get("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/works/{workId}/updates/{updateId}", workId, updateId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("越權修改", null, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/works/{workId}/updates/{updateId}", workId, updateId)
                .header(HttpHeaders.AUTHORIZATION, bearer(otherToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void blankAndOversizedUpdatesAreRejected() throws Exception {
        String token = register("update-validation", "update-validation@example.com");
        long workId = createWork(token, "驗證議題", null);

        mockMvc.perform(post("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("   ", null, "")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("a".repeat(50001), null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletingWorkCascadesToProgressUpdates() throws Exception {
        String token = register("cascade-owner", "cascade-owner@example.com");
        long workId = createWork(token, "可刪除議題", null);
        long updateId = createUpdate(token, workId, "即將隨議題刪除", null, null);

        workRepository.deleteById(workId);
        workRepository.flush();

        assertThat(updateRepository.findById(updateId)).isEmpty();
    }

    private String register(String username, String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new RegistrationRequest(username, email, "password123"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private long createWork(String token, String title, String currentAssessment) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/works")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new CreateWorkPayload(title, null, null, currentAssessment, null, null))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private long createUpdate(
            String token,
            long workId,
            String changeSummary,
            String assessment,
            String nextStep) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/works/{workId}/updates", workId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(changeSummary, assessment, nextStep)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String updateJson(String changeSummary, String assessment, String nextStep) throws Exception {
        return objectMapper.writeValueAsString(
                new ProgressUpdatePayload(changeSummary, assessment, nextStep));
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

    private record ProgressUpdatePayload(String changeSummary, String assessment, String nextStep) {}
}
