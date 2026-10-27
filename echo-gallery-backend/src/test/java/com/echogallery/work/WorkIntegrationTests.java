package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.card.*;
import com.echogallery.experiment.*;
import com.echogallery.user.UserRepository;
import com.echogallery.demo.DemoSessionService;
import tools.jackson.databind.*;

@AutoConfigureMockMvc @Transactional
class WorkIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CreativeWorkRepository works;
    @Autowired WorkSourceRepository sources;
    @Autowired CardRepository cards;
    @Autowired UserRepository users;
    @Autowired ExperimentRepository experiments;
    @Autowired ExperimentExplorationRecordRepository records;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired DemoSessionService demo;
    @Autowired Clock clock;
    private static final String ROOT = "/api/creative-works";

    @Test void filtersMultipleExperimentsAndStagesBeforePaginationWithoutLeakingOwners() throws Exception {
        String token = register(); long first = create(token, "寫作作品"), second = create(token, "工程作品");
        long unassigned = create(token, "無歸屬"), excluded = create(token, "其他階段");
        var user = works.findById(first).orElseThrow().getUser();
        var writing = new Experiment(); writing.setUser(user); writing.setTitle("寫作"); writing = experiments.saveAndFlush(writing);
        var engineering = new Experiment(); engineering.setUser(user); engineering.setTitle("工程"); engineering = experiments.saveAndFlush(engineering);
        send(put(ROOT + "/{id}", first), token, Map.of("title", "寫作作品", "editorMode", "INTERNAL", "experimentId", writing.getId())).andExpect(status().isOk());
        send(put(ROOT + "/{id}", second), token, Map.of("title", "工程作品", "editorMode", "INTERNAL", "experimentId", engineering.getId())).andExpect(status().isOk());
        send(put(ROOT + "/{id}/stage", second), token, Map.of("stage", "REVISING")).andExpect(status().isOk());
        send(put(ROOT + "/{id}/stage", excluded), token, Map.of("stage", "SHAREABLE")).andExpect(status().isOk());
        create(register(), "其他帳號");
        String ids = writing.getId() + "," + engineering.getId();
        send(get(ROOT).param("experimentIds", ids).param("stages", "WORKING,REVISING").param("size", "1"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)).andExpect(jsonPath("$.content.length()").value(1));
        send(get(ROOT).param("experimentIds", ids).param("unassigned", "true").param("stages", "WORKING,REVISING"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3));
        send(get(ROOT).param("experimentId", writing.getId().toString()).param("stage", "WORKING"), token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(first)).andExpect(jsonPath("$.totalElements").value(1));
        send(get(ROOT).param("unassigned", "true").param("stages", "WORKING"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(unassigned)).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void savesExactlyAndRejectsStalePagesWithoutPolling() throws Exception {
        String token = register(); long id = create(token, "正文測試");
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "原文  \n", "expectedVersion", 0))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bodyVersion").value(1));
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "原文  \n", "expectedVersion", 1))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bodyVersion").value(1));
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "舊頁覆寫", "expectedVersion", 0)).andExpect(status().isConflict());
        send(get(ROOT + "/{id}", id), token, null).andExpect(jsonPath("$.body").value("原文  \n"));
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "", "expectedVersion", 1)).andExpect(jsonPath("$.bodyVersion").value(2));
        send(put(ROOT + "/{id}/archive", id), token, null).andExpect(status().isOk());
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "封存不可寫", "expectedVersion", 2)).andExpect(status().isConflict());
        send(put(ROOT + "/{id}/restore", id), token, null).andExpect(status().isOk());
        send(put(ROOT + "/{id}", id), token, Map.of("title", "外部", "editorMode", "EXTERNAL", "externalUrl", "https://example.com/file")).andExpect(status().isOk());
        send(put(ROOT + "/{id}/body", id), token, Map.of("body", "不可覆寫", "expectedVersion", 2)).andExpect(status().isConflict());
        send(put(ROOT + "/{id}", id), token, Map.of("title", "外部", "editorMode", "INTERNAL", "externalUrl", "javascript:alert(1)")).andExpect(status().isBadRequest());
    }
    @Test void sourcesRemainIndependentAndDeleteSafely() throws Exception {
        String token = register(); long work = create(token, "跨域素材");
        // 從作品取得真正 owner，不以範例 ID 推測歸屬。
        var user = works.findById(work).orElseThrow().getUser();
        var card = cards.saveAndFlush(Card.builder().user(user).title("封存素材").type("note").content("卡片全文").isArchived(true).build());
        var exp = new Experiment(); exp.setUser(user); exp.setTitle("來源實驗"); exp.setArchived(true); exp = experiments.saveAndFlush(exp);
        var payload = Map.of("sources", List.of(Map.of("cardId", card.getId())));
        send(post(ROOT + "/{id}/sources", work), token, payload).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        send(post(ROOT + "/{id}/sources", work), token, payload).andExpect(jsonPath("$.length()").value(1));
        send(get(ROOT + "/candidates").param("kind", "RECORD"), token, null).andExpect(status().isBadRequest());
        send(get(ROOT + "/usages").param("cardIds", card.getId().toString()), token, null).andExpect(jsonPath("$[0].workId").value(work));
        send(get(ROOT + "/{id}", work), token, null).andExpect(jsonPath("$.work.experimentId").isEmpty()).andExpect(jsonPath("$.body").value(""));
        send(put(ROOT + "/{id}", work), token, Map.of("title", "跨域素材", "editorMode", "INTERNAL", "experimentId", exp.getId())).andExpect(status().isOk());
        send(put(ROOT + "/{id}/body", work), token, Map.of("body", "已貼入的文字仍保留", "expectedVersion", 0)).andExpect(status().isOk());
        em.flush(); em.clear();
        cards.delete(card); experiments.delete(exp); em.flush(); em.clear();
        assertThat(sources.findByWorkIdOrderByIdDesc(work)).isEmpty();
        assertThat(works.findById(work)).isPresent();
        send(get(ROOT + "/{id}", work), token, null).andExpect(jsonPath("$.work.experimentId").isEmpty()).andExpect(jsonPath("$.body").value("已貼入的文字仍保留"));
    }
    @Test void temporaryRecordsCannotBeAddedToWorkSources() throws Exception {
        String token = register(); long work = create(token, "卡片素材");
        send(get(ROOT + "/candidates").param("kind", "RECORD"), token, null).andExpect(status().isBadRequest());
        send(post(ROOT + "/{id}/sources", work), token, Map.of("sources", List.of(Map.of("recordId", 999L))))
                .andExpect(status().isBadRequest());
        send(get(ROOT + "/{id}/sources", work), token, null).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void foreignOwnersCannotReadWriteOrAttach() throws Exception {
        String a = register(), b = register(); long work = create(a, "私人作品"), other = create(b, "他人作品");
        send(get(ROOT + "/{id}", work), b, null).andExpect(status().isNotFound());
        send(delete(ROOT + "/{id}", work), b, null).andExpect(status().isNotFound());
        var card = cards.saveAndFlush(Card.builder().user(works.findById(other).orElseThrow().getUser()).type("note").title("私有卡片").build());
        send(post(ROOT + "/{id}/sources", work), a, Map.of("sources", List.of(Map.of("cardId", card.getId())))).andExpect(status().isNotFound());
        long count = works.count();
        assertThat(count).isEqualTo(2);
        send(get(ROOT + "/candidates").param("kind", "CARD").param("keyword", "私有卡片"), a, null).andExpect(jsonPath("$.totalElements").value(0));
    }
    @Test void newTreasureAndUnlinkKeepIndependentLifecycles() throws Exception {
        String token = register(); long work = create(token, "獨立成果");
        send(put(ROOT + "/{id}/stage", work), token, Map.of("stage", "SHAREABLE")).andExpect(status().isOk());
        long treasure = json(send(post(ROOT + "/{id}/treasure", work), token, Map.of("title", "獨立成果", "acquiredOn", LocalDate.now(clock).toString())).andExpect(status().isOk())).get("work").get("treasureId").asLong();
        send(get("/api/treasures/{id}", treasure), token, null).andExpect(jsonPath("$.rank").value("STONE"));
        send(put(ROOT + "/{id}/stage", work), token, Map.of("stage", "REVISING")).andExpect(status().isOk());
        send(delete(ROOT + "/{id}/treasure", work), token, null).andExpect(jsonPath("$.work.treasureId").isEmpty());
        send(get("/api/treasures/{id}", treasure), token, null).andExpect(status().isOk());
        send(delete(ROOT + "/{id}", work), token, null).andExpect(status().isNoContent());
        send(get("/api/treasures/{id}", treasure), token, null).andExpect(status().isOk());
    }
    @Test void stagesPublicationAndTreasureAreIndependent() throws Exception {
        String token = register(); long work = create(token, "完成作品");
        var content = Map.of("title", "原有奇珍", "description", "保留策展說明", "acquiredOn", LocalDate.now(clock).toString());
        long treasure = json(send(post("/api/treasures"), token, content)).get("id").asLong();
        send(put(ROOT + "/{id}/treasure", work), token, Map.of("treasureId", treasure)).andExpect(status().isConflict());
        send(put(ROOT + "/{id}/stage", work), token, Map.of("stage", "SHAREABLE", "firstCompletedOn", LocalDate.now(clock).toString())).andExpect(status().isOk());
        send(put(ROOT + "/{id}/treasure", work), token, Map.of("treasureId", treasure)).andExpect(jsonPath("$.work.treasureId").value(treasure));
        send(get("/api/treasures/{id}", treasure), token, null).andExpect(jsonPath("$.description").value("保留策展說明"));
        send(put(ROOT + "/{id}/stage", work), token, Map.of("stage", "REVISING")).andExpect(jsonPath("$.work.treasureId").value(treasure));
        send(put(ROOT + "/{id}/share", work), token, Map.of("published", false, "shareUrl", "https://example.com/private")).andExpect(jsonPath("$.work.published").value(false));
        send(get(ROOT).param("stage", "REVISING").param("published", "false"), token, null).andExpect(jsonPath("$.totalElements").value(1));
        em.flush(); em.clear();
        send(delete("/api/treasures/{id}", treasure), token, null).andExpect(status().isNoContent());
        em.flush(); em.clear();
        send(get(ROOT + "/{id}", work), token, null).andExpect(jsonPath("$.work.treasureId").isEmpty());
    }
    @Test void demoWorkCanBeEditedResetAndExpiredWithAllDependencies() throws Exception {
        var session = json(send(post("/api/auth/demo-sessions"), null, Map.of("library", "writing")).andExpect(status().isOk()));
        String token = session.get("token").asText(); long owner = session.get("id").asLong();
        var result = json(send(get(ROOT), token, null)); long work = result.get("content").get(0).get("id").asLong();
        send(put(ROOT + "/{id}/body", work), token, Map.of("body", "示範續寫", "expectedVersion", 0)).andExpect(status().isOk());
        var userBeforeReset = users.findById(owner).orElseThrow();
        var card = cards.saveAndFlush(Card.builder().user(userBeforeReset).type("note").title("隔離 Demo 素材").build());
        send(post(ROOT + "/{id}/sources", work), token, Map.of("sources", List.of(Map.of("cardId", card.getId())))).andExpect(status().isOk());
        send(put(ROOT + "/{id}/stage", work), token, Map.of("stage", "SHAREABLE")).andExpect(status().isOk());
        send(post(ROOT + "/{id}/treasure", work), token, Map.of("title", "Demo 入藏", "acquiredOn", LocalDate.now(clock).toString())).andExpect(status().isOk());
        send(post("/api/auth/demo-sessions/reset"), token, null).andExpect(status().isOk());
        // 模擬重設請求完成後的持久化邊界，避免測試外層交易保留已批次刪除的舊實體。
        em.flush(); em.clear();
        assertThat(works.existsById(work)).isFalse();
        assertThat(sources.findByWorkIdOrderByIdDesc(work)).isEmpty();
        send(get(ROOT), token, null).andExpect(jsonPath("$.totalElements").value(1));
        var user = users.findById(owner).orElseThrow(); user.setDemoExpiresAt(ZonedDateTime.now(clock).minusMinutes(1)); users.saveAndFlush(user);
        demo.clearExpiredSessions(); em.flush(); em.clear();
        assertThat(users.existsById(owner)).isFalse();
    }
    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString();
        return json(send(post("/api/auth/register"), null, Map.of("username", "work-" + suffix.substring(0, 8), "email", suffix + "@example.com", "password", "password123")).andExpect(status().isOk())).get("token").asText();
    }
    private long create(String token, String title) throws Exception { return json(send(post(ROOT), token, Map.of("title", title, "editorMode", "INTERNAL")).andExpect(status().isOk())).get("work").get("id").asLong(); }
    private ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(req);
    }
    private JsonNode json(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
}
