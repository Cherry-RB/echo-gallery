package com.echogallery.experiment;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.transaction.TestTransaction;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.UserRepository;
import com.echogallery.treasure.TreasureLifecycle;
import com.echogallery.card.CardRepository;
import tools.jackson.databind.*;

@AutoConfigureMockMvc @Transactional
class ExplorationActivityIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired ExperimentExplorationRecordRepository records;
    @Autowired ExplorationThreadMethodRepository methodRepository;
    @Autowired ExperimentMethodPreferenceRepository preferenceRepository;
    @Autowired com.echogallery.work.CreativeWorkRepository workRepository;
    @Autowired UserRepository users;
    @Autowired ExperimentRepository experiments;
    @Autowired CardRepository cards;
    @Autowired TreasureLifecycle lifecycle;
    @Autowired TransactionTemplate transactions;
    private final List<Long> committedOwners = new ArrayList<>();

    @AfterEach void cleanupCommittedFixtures() {
        if (committedOwners.isEmpty()) return;
        transactions.executeWithoutResult(tx -> committedOwners.forEach(owner -> {
            lifecycle.clearOwner(owner);
            experiments.deleteAll(experiments.findAllByUserId(owner)); experiments.flush();
            cards.deleteAll(cards.findAllByUserId(owner)); cards.flush();
            users.deleteById(owner);
        }));
    }

    record Context(String token, long id) { String root() { return "/api/experiments/" + id; } String activity() { return root() + "/activity"; } }
    Context context() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        var auth = json(post("/api/auth/register"), null, Map.of("username", email, "email", email, "password", "password123"));
        if (!TestTransaction.isActive()) committedOwners.add(auth.get("id").asLong());
        String token = auth.get("token").asText();
        long id = json(post("/api/experiments"), token, Map.of("title", "隔離探索測試")).get("id").asLong();
        return new Context(token, id);
    }
    JsonNode json(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        return mapper.readTree(send(request, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }
    JsonNode focus(Context c, String content) throws Exception {
        return json(put(c.activity() + "/focus"), c.token(), Map.of("newContent", content, "expectedVersion", 0));
    }
    JsonNode discovery(Context c, long thread, Long method, String text) throws Exception {
        var request = new LinkedHashMap<String,Object>(); request.put("threadId", thread); request.put("methodId", method); request.put("discovery", text);
        if (method != null) request.put("expectedMethodVersion", json(get(c.activity() + "/methods/" + method), c.token(), null).get("version").asLong());
        return json(post(c.activity() + "/records"), c.token(), request);
    }
    Map<String,Object> group(long thread, Long method, List<Long> ids, boolean all, boolean includeMethod) {
        var group = new LinkedHashMap<String,Object>(); group.put("threadId", thread); group.put("methodId", method);
        group.put("recordIds", ids); group.put("allRecords", all); group.put("includeMethod", includeMethod); return group;
    }
    JsonNode preview(Context c, String mode, List<Map<String,Object>> groups) throws Exception {
        return json(post(c.activity() + "/organizations/preview"), c.token(), Map.of("mode", mode, "groups", groups));
    }
    Map<String,Object> commit(JsonNode preview, boolean clear) {
        var request = new LinkedHashMap<String,Object>(); request.put("token", preview.get("token").asText());
        request.put("requestId", UUID.randomUUID().toString());
        List<Map<String,String>> drafts = new ArrayList<>();
        for (var group : preview.get("groups")) drafts.add(Map.of("key", group.get("key").asText(), "text", group.get("text").asText() + "本輪心得"));
        request.put("drafts", drafts); request.put("clearRecords", clear); return request;
    }

    JsonNode history(Context c, long thread, Long method) throws Exception {
        var request = get(c.activity() + "/history").param("threadId", "" + thread);
        if (method != null) request.param("methodId", "" + method);
        return json(request, c.token(), null);
    }
    Map<String,Object> historyUpdate(long thread, Long method, String text, long version) {
        var request = new LinkedHashMap<String,Object>();
        request.put("threadId", thread); request.put("methodId", method); request.put("text", text); request.put("expectedVersion", version);
        return request;
    }
    long newThread(Context c, String name) throws Exception {
        return json(post(c.root() + "/threads"), c.token(), Map.of("name", name)).get("id").asLong();
    }
    JsonNode newMethod(Context c, long thread, String content) throws Exception {
        return json(post(c.activity() + "/methods"), c.token(), Map.of("threadId", thread, "content", content));
    }
    JsonNode moveMethod(Context c, long method, long target, long version) throws Exception {
        return json(put(c.activity() + "/methods/" + method + "/assignment"), c.token(), Map.of("targetThreadId", target, "expectedVersion", version));
    }

    @Test void historyEditingPreservesFindingsFocusAndOrganizationTimeAndInvalidatesOldPreviews() throws Exception {
        var c = context(); var state = focus(c, "保留行動");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var finding = discovery(c, thread, method, "原始發現");
        var persistedFinding = json(get(c.activity() + "/records"), c.token(), null).get("content").get(0);
        var groups = List.of(group(thread, method, List.of(), true, true));
        json(post(c.activity() + "/organizations"), c.token(), commit(preview(c, "HISTORY", groups), false));
        var before = history(c, thread, method);
        var stalePreview = preview(c, "CARD", groups);
        String text = "  修正心得\n\n---\n\n補充 😀\n";
        var updated = json(put(c.activity() + "/history"), c.token(), historyUpdate(thread, method, text, before.get("version").asLong()));
        assertThat(updated.get("text").asText()).isEqualTo(text);
        assertThat(updated.get("version").asLong()).isEqualTo(before.get("version").asLong() + 1);
        assertThat(updated.get("lastOrganizedAt")).isEqualTo(before.get("lastOrganizedAt"));
        assertThat(history(c, thread, method)).isEqualTo(updated);
        assertThat(json(get(c.activity()), c.token(), null).get("currentTry").asText()).isEqualTo("保留行動");
        var preserved = json(get(c.activity() + "/records"), c.token(), null).get("content").get(0);
        assertThat(preserved.get("discovery")).isEqualTo(finding.get("discovery"));
        assertThat(preserved.get("occurredAt")).isEqualTo(persistedFinding.get("occurredAt"));
        var staleRequest = commit(stalePreview, false); staleRequest.put("cardContent", "不應寫入卡片");
        send(post(c.activity() + "/organizations"), c.token(), staleRequest).andExpect(status().isConflict());
        send(put(c.activity() + "/history"), c.token(), historyUpdate(thread, method, "舊版本", before.get("version").asLong())).andExpect(status().isConflict());
        var unchanged = json(put(c.activity() + "/history"), c.token(), historyUpdate(thread, method, text, updated.get("version").asLong()));
        assertThat(unchanged).isEqualTo(updated);
    }

    @Test void emptyThreadHistoryCanBeCreatedEditedClearedAndDoesNotChangeFindings() throws Exception {
        var c = context(); long thread = newThread(c, "沒有行動的探索線");
        var original = history(c, thread, null);
        assertThat(original.get("text").asText()).isEmpty();
        String text = "手動貼上\n" + "長文字".repeat(20000);
        var added = json(put(c.activity() + "/history"), c.token(), historyUpdate(thread, null, text, original.get("version").asLong()));
        assertThat(added.get("text").asText()).isEqualTo(text);
        assertThat(added.get("lastOrganizedAt").isNull()).isTrue();
        assertThat(json(get(c.root() + "/threads/" + thread), c.token(), null).get("hasUnassignedHistory").asBoolean()).isTrue();
        var cleared = json(put(c.activity() + "/history"), c.token(), historyUpdate(thread, null, " \n\t", added.get("version").asLong()));
        assertThat(cleared.get("text").asText()).isEmpty();
        assertThat(json(get(c.root() + "/threads/" + thread), c.token(), null).get("hasUnassignedHistory").asBoolean()).isFalse();
        assertThat(records.countByExperimentId(c.id())).isZero();
    }

    @Test void historyRejectsForeignOwnersMismatchedScopesAndInvalidRequests() throws Exception {
        var c = context(); var other = context(); var state = focus(c, "我的行動");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        long sibling = newThread(c, "其他探索線");
        send(put(c.activity() + "/history"), other.token(), historyUpdate(thread, method, "越權", 0)).andExpect(status().isNotFound());
        send(put(other.activity() + "/history"), other.token(), historyUpdate(thread, method, "跨實驗", 0)).andExpect(status().isNotFound());
        send(put(c.activity() + "/history"), c.token(), historyUpdate(sibling, method, "不符歸屬", 0)).andExpect(status().isBadRequest());
        var invalid = historyUpdate(thread, method, "草稿", 0); invalid.remove("expectedVersion");
        send(put(c.activity() + "/history"), c.token(), invalid).andExpect(status().isBadRequest());
        invalid.put("expectedVersion", -1);
        send(put(c.activity() + "/history"), c.token(), invalid).andExpect(status().isBadRequest());
        invalid.put("expectedVersion", 0); invalid.put("text", null);
        send(put(c.activity() + "/history"), c.token(), invalid).andExpect(status().isBadRequest());
        assertThat(history(c, thread, method).get("text").asText()).isEmpty();
    }

    @Test void movingMethodPreservesIdentityHistoryPreferencesAndUpdatesAllFindingScopes() throws Exception {
        var c = context(); long source = newThread(c, "A"), target = newThread(c, "B");
        long method = newMethod(c, source, "歸錯類的行動").get("id").asLong();
        json(put(c.activity() + "/focus"), c.token(), Map.of("threadId", source, "methodId", method, "expectedVersion", 0));
        json(put(c.activity() + "/favorites"), c.token(), Map.of("methodIds", List.of(method), "expectedVersion", 1));
        var first = discovery(c, source, method, "發現一"); var second = discovery(c, source, method, "發現二");
        var group = List.of(group(source, method, List.of(), true, true));
        json(post(c.activity() + "/organizations"), c.token(), commit(preview(c, "HISTORY", group), false));
        var historyBefore = history(c, source, method);
        var occurredBefore = new HashMap<Long,java.time.Instant>();
        for (var r : json(get(c.activity() + "/records"), c.token(), null).get("content"))
            occurredBefore.put(r.get("id").asLong(), java.time.ZonedDateTime.parse(r.get("occurredAt").asText()).toInstant());
        var stateBefore = json(get(c.activity()), c.token(), null);
        var createdAt = methodRepository.findById(method).orElseThrow().getCreatedAt();
        var lastUsedAt = preferenceRepository.findByExperimentIdAndMethodId(c.id(), method).orElseThrow().getLastUsedAt();
        var stale = preview(c, "HISTORY", group);
        var moved = moveMethod(c, method, target, historyBefore.get("version").asLong());
        assertThat(moved.get("id").asLong()).isEqualTo(method);
        assertThat(moved.get("content").asText()).isEqualTo("歸錯類的行動");
        assertThat(moved.get("threadId").asLong()).isEqualTo(target);
        assertThat(moved.get("discoveryCount").asLong()).isEqualTo(2);
        assertThat(history(c, target, method).get("text")).isEqualTo(historyBefore.get("text"));
        assertThat(history(c, target, method).get("lastOrganizedAt")).isEqualTo(historyBefore.get("lastOrganizedAt"));
        assertThat(methodRepository.findById(method).orElseThrow().getCreatedAt()).isEqualTo(createdAt);
        assertThat(preferenceRepository.findByExperimentIdAndMethodId(c.id(), method).orElseThrow().getLastUsedAt()).isEqualTo(lastUsedAt);
        var stateAfter = json(get(c.activity()), c.token(), null);
        assertThat(stateAfter.get("currentThreadId").asLong()).isEqualTo(target);
        assertThat(stateAfter.get("currentMethod").get("id").asLong()).isEqualTo(method);
        assertThat(stateAfter.get("favoriteMethods").get(0).get("id").asLong()).isEqualTo(method);
        assertThat(stateAfter.get("recentMethods").get(0).get("id")).isEqualTo(stateBefore.get("recentMethods").get(0).get("id"));
        for (var finding : List.of(first, second)) {
            var stored = records.findById(finding.get("id").asLong()).orElseThrow();
            assertThat(stored.getThread().getId()).isEqualTo(target);
            assertThat(stored.getVersion()).isEqualTo(finding.get("version").asLong() + 1);
            var persisted = json(get(c.activity() + "/records").param("methodId", "" + method), c.token(), null).get("content");
            for (var r : persisted) if (r.get("id").equals(finding.get("id")))
                assertThat(java.time.ZonedDateTime.parse(r.get("occurredAt").asText()).toInstant()).isEqualTo(occurredBefore.get(r.get("id").asLong()));
        }
        send(post(c.activity() + "/organizations"), c.token(), commit(stale, false)).andExpect(status().isConflict());
        send(put(c.activity() + "/history"), c.token(), historyUpdate(source, method, "舊歸屬草稿", historyBefore.get("version").asLong())).andExpect(status().isBadRequest());
        send(delete(c.root() + "/threads/" + source), c.token(), null).andExpect(status().isNoContent());
        assertThat(records.countByMethodId(method)).isEqualTo(2);
        assertThat(history(c, target, method).get("text")).isEqualTo(historyBefore.get("text"));
    }

    @Test void movingMethodRejectsConflictsForeignTargetsAndStaleVersionsWithoutPartialChanges() throws Exception {
        var c = context(); var other = context();
        long source = newThread(c, "A"), target = newThread(c, "B"), foreign = newThread(other, "他人探索線");
        long method = newMethod(c, source, "同名行動").get("id").asLong();
        newMethod(c, target, "同名行動");
        var finding = discovery(c, source, method, "保留原歸屬");
        var body = Map.of("targetThreadId", target, "expectedVersion", 1);
        send(put(c.activity() + "/methods/" + method + "/assignment"), c.token(), body).andExpect(status().isConflict());
        send(put(c.activity() + "/methods/" + method + "/assignment"), other.token(), body).andExpect(status().isNotFound());
        send(put(c.activity() + "/methods/" + method + "/assignment"), c.token(), Map.of("targetThreadId", foreign, "expectedVersion", 1)).andExpect(status().isNotFound());
        send(put(c.activity() + "/methods/" + method + "/assignment"), c.token(), Map.of("targetThreadId", target, "expectedVersion", 0)).andExpect(status().isConflict());
        send(put(c.activity() + "/methods/" + method + "/assignment"), c.token(), Map.of("targetThreadId", target)).andExpect(status().isBadRequest());
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("threadId").asLong()).isEqualTo(source);
        assertThat(records.findById(finding.get("id").asLong()).orElseThrow().getThread().getId()).isEqualTo(source);
    }

    @Test void movingAnEmptyMethodLeavesOtherFocusMaterialsWorksAndUnassignedHistoryAtTheirOriginalScopes() throws Exception {
        var c = context(); long source = newThread(c, "A"), target = newThread(c, "B");
        long method = newMethod(c, source, "只有行動文字").get("id").asLong();
        long current = newMethod(c, target, "目前採用的另一個行動").get("id").asLong();
        var focusBefore = json(put(c.activity() + "/focus"), c.token(), Map.of("threadId", target, "methodId", current, "expectedVersion", 0));
        json(put(c.activity() + "/history"), c.token(), historyUpdate(source, null, "A 的未指定行動歷史", 0));
        long card = json(post("/api/cards"), c.token(), Map.of("type", "note", "title", "原線卡片", "content", "保留正文")).get("id").asLong();
        for (String role : List.of("MATERIAL", "RESULT"))
            json(post(c.root() + "/threads/" + source + "/cards"), c.token(), Map.of("cardId", card, "role", role, "note", "原關聯備註"));
        var work = new com.echogallery.work.CreativeWork();
        work.setUser(experiments.findById(c.id()).orElseThrow().getUser()); work.setTitle("原線作品");
        work.setCreatedAt(java.time.ZonedDateTime.now()); work.setEditedAt(java.time.ZonedDateTime.now());
        workRepository.saveAndFlush(work);
        json(post(c.root() + "/threads/" + source + "/works"), c.token(), Map.of("workId", work.getId()));
        var moved = moveMethod(c, method, target, 0);
        assertThat(moved.get("discoveryCount").asLong()).isZero();
        assertThat(moved.get("hasHistory").asBoolean()).isFalse();
        assertThat(json(get(c.activity()), c.token(), null).get("currentMethod").get("id")).isEqualTo(focusBefore.get("currentMethod").get("id"));
        assertThat(json(get(c.activity()), c.token(), null).get("version")).isEqualTo(focusBefore.get("version"));
        assertThat(history(c, source, null).get("text").asText()).isEqualTo("A 的未指定行動歷史");
        var sourceAfter = json(get(c.root() + "/threads/" + source), c.token(), null);
        var targetAfter = json(get(c.root() + "/threads/" + target), c.token(), null);
        assertThat(sourceAfter.get("cards").size()).isEqualTo(2);
        assertThat(sourceAfter.get("works").size()).isEqualTo(1);
        assertThat(sourceAfter.get("cards").get(0).get("note").asText()).isEqualTo("原關聯備註");
        assertThat(targetAfter.get("cards").size()).isZero(); assertThat(targetAfter.get("works").size()).isZero();
        assertThat(targetAfter.get("methods").get(1).get("id").asLong()).isEqualTo(method);
        assertThat(moveMethod(c, method, target, moved.get("version").asLong())).isEqualTo(moved);
        json(put(c.activity() + "/history"), c.token(), historyUpdate(target, method, "手動心得", moved.get("version").asLong()));
        json(put(c.activity() + "/history"), c.token(), historyUpdate(target, method, "", moved.get("version").asLong() + 1));
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("hasHistory").asBoolean()).isFalse();
        assertThat(json(get("/api/cards/" + card), c.token(), null).get("content").asText()).isEqualTo("保留正文");
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void editedAndMovedDataCanBeReadAndCleanedThroughTheExistingLifecycle() throws Exception {
        var c = context(); long source = newThread(c, "A"), target = newThread(c, "B");
        long method = newMethod(c, source, "清理生命週期").get("id").asLong();
        discovery(c, source, method, "整合測試專用發現");
        json(put(c.activity() + "/history"), c.token(), historyUpdate(source, method, "測試歷史", 1));
        var moved = moveMethod(c, method, target, 2);
        assertThat(history(c, target, method).get("text").asText()).isEqualTo("測試歷史");
        send(delete(c.root() + "/threads/" + source), c.token(), null).andExpect(status().isNoContent());
        assertThat(history(c, target, method).get("text").asText()).isEqualTo("測試歷史");
        send(delete(c.activity() + "/methods/" + method).param("expectedVersion", moved.get("version").asText()), c.token(), null).andExpect(status().isOk());
        assertThat(history(c, target, null).get("text").asText()).contains("測試歷史");
        send(delete(c.root() + "/threads/" + target), c.token(), null).andExpect(status().isNoContent());
        send(delete(c.root()), c.token(), null).andExpect(status().isNoContent());
        assertThat(records.countByExperimentId(c.id())).isZero();
    }

    @Test void unassignedIsLazyAndBothScreensUseTheSameDiscoveries() throws Exception {
        var c = context();
        assertThat(json(get(c.root() + "/threads/summaries"), c.token(), null).get("totalElements").asLong()).isZero();
        var state = focus(c, "睡前閱讀二十分鐘");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var raw = discovery(c, thread, null, "房間太亮");
        assertThat(raw.get("methodId").isNull()).isTrue();
        var finding = discovery(c, thread, method, "比較容易入睡");
        var detail = json(get(c.root() + "/threads/" + thread), c.token(), null);
        assertThat(detail.get("recordCount").asLong()).isEqualTo(2);
        assertThat(json(get(c.activity()), c.token(), null).get("currentMethod").get("id").asLong()).isEqualTo(method);
        var assigned = json(put(c.activity() + "/records/" + raw.get("id").asLong() + "/assignment"), c.token(),
            Map.of("threadId", thread, "methodId", method, "expectedVersion", 0, "expectedMethodVersion", 1));
        assertThat(assigned.get("occurredAt")).isEqualTo(raw.get("occurredAt"));
        assertThat(json(get(c.activity() + "/methods"), c.token(), null).get("content").get(0).get("discoveryCount").asLong()).isEqualTo(2);
        assertThat(records.countByExperimentId(c.id())).isEqualTo(2);
        send(delete(c.root() + "/threads/" + thread), c.token(), null).andExpect(status().isConflict());
        send(put(c.root() + "/threads/" + thread), c.token(), Map.of("name", "不能改名", "expectedVersion", detail.get("version").asLong())).andExpect(status().isConflict());
        assertThat(finding.get("threadId").asLong()).isEqualTo(thread);
    }

    @Test void changingMethodRequiresFullOrganizationAndRejectsAnUnseenDiscovery() throws Exception {
        var c = context(); var state = focus(c, "閱讀二十分鐘");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        discovery(c, thread, method, "星期一容易入睡");
        send(put(c.activity() + "/methods/" + method), c.token(), Map.of("content", "閱讀三十分鐘", "expectedVersion", 1)).andExpect(status().isConflict());
        var groups = List.of(group(thread, method, List.of(), true, true));
        var stale = preview(c, "UPDATE_METHOD", groups);
        discovery(c, thread, method, "星期二的不同觀察");
        var staleRequest = commit(stale, true); staleRequest.put("newMethodContent", "閱讀三十分鐘");
        send(post(c.activity() + "/organizations"), c.token(), staleRequest).andExpect(status().isConflict());
        assertThat(records.countByExperimentId(c.id())).isEqualTo(2);
        var request = commit(preview(c, "UPDATE_METHOD", groups), true); request.put("newMethodContent", "閱讀三十分鐘");
        var result = json(post(c.activity() + "/organizations"), c.token(), request);
        assertThat(result.get("clearedCount").asInt()).isEqualTo(2);
        assertThat(records.countByExperimentId(c.id())).isZero();
        var history = json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText();
        assertThat(history).contains("閱讀二十分鐘", "星期一容易入睡", "星期二的不同觀察", "整理心得");
        assertThat(json(get(c.activity()), c.token(), null).get("currentTry").asText()).isEqualTo("閱讀三十分鐘");
        assertThat(json(post(c.activity() + "/organizations"), c.token(), request)).isEqualTo(result);
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText()).isEqualTo(history);
        request.put("newMethodContent", "不同重送"); send(post(c.activity() + "/organizations"), c.token(), request).andExpect(status().isConflict());
    }

    @Test void generatedOrganizationTextUsesTheApplicationTimezoneAndMinutePrecision() throws Exception {
        var c = context(); var state = focus(c, "格式驗證行動");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        json(post(c.activity() + "/records"), c.token(), Map.of("threadId", thread, "methodId", method,
            "discovery", "觀察內容保持原樣", "occurredAt", "2026-09-26T14:23:11.695108Z", "expectedMethodVersion", 0));
        var groups = List.of(group(thread, method, List.of(), true, true));
        for (String mode : List.of("HISTORY", "CARD", "UPDATE_METHOD")) {
            var formatted = preview(c, mode, groups);
            assertThat(formatted.get("groups").get(0).get("text").asText()).contains("2026-09-26 22:23：\n觀察內容保持原樣").doesNotContain("T14:23", "695108Z");
            assertThat(formatted.get("cardContent").asText()).contains("2026-09-26 22:23");
        }
        var p = preview(c, "HISTORY", groups);
        json(post(c.activity() + "/organizations"), c.token(), commit(p, false));
        var history = json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText();
        assertThat(history).contains("紀錄期間：2026-09-26 22:23 ～ 2026-09-26 22:23").doesNotContain("T14:23", "695108Z", "[Asia/Taipei]");
        assertThat(history).containsPattern("整理時間：\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}\\n");
    }

    @Test void unchangedUpdatesAreRejectedBeforeAnyFindingOrHistoryChanges() throws Exception {
        var c = context(); var state = focus(c, "原行動");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var update = new LinkedHashMap<String, Object>();
        update.put("methodId", method); update.put("newContent", "  原行動  ");
        update.put("action", "UPDATE"); update.put("expectedVersion", 1); update.put("expectedMethodVersion", 0);
        send(put(c.activity() + "/focus"), c.token(), update).andExpect(status().isBadRequest());
        send(put(c.activity() + "/methods/" + method), c.token(), Map.of("content", "  原行動  ", "expectedVersion", 0))
            .andExpect(status().isBadRequest());
        assertThat(json(get(c.activity()), c.token(), null).get("version").asLong()).isEqualTo(1);
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("version").asLong()).isZero();

        var finding = discovery(c, thread, method, "必須保留的發現");
        update.put("expectedMethodVersion", 1);
        send(put(c.activity() + "/focus"), c.token(), update).andExpect(status().isBadRequest());
        var groups = List.of(group(thread, method, List.of(), true, true));
        var request = commit(preview(c, "UPDATE_METHOD", groups), true);
        request.put("newMethodContent", "  原行動  ");
        send(post(c.activity() + "/organizations"), c.token(), request).andExpect(status().isBadRequest());
        assertThat(records.findById(finding.get("id").asLong())).isPresent();
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("version").asLong()).isEqualTo(1);
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText()).isEmpty();

        json(post(c.activity() + "/organizations"), c.token(), commit(preview(c, "HISTORY", groups), true));
        assertThat(records.findById(finding.get("id").asLong())).isEmpty();
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("content").asText()).isEqualTo("原行動");
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText()).contains("必須保留的發現");
    }

    @Test void selectedFindingsAndUnassignedHistoryStayInTheirOwnGroups() throws Exception {
        var c = context(); var state = focus(c, "測試試法");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var first = discovery(c, thread, method, "選取的觀察"); discovery(c, thread, method, "保留的觀察");
        var lone = discovery(c, thread, null, "沒有試法的觀察");
        var selected = List.of(group(thread, method, List.of(first.get("id").asLong()), false, false),
            group(thread, null, List.of(lone.get("id").asLong()), false, false));
        json(post(c.activity() + "/organizations"), c.token(), commit(preview(c, "HISTORY", selected), false));
        assertThat(records.countByExperimentId(c.id())).isEqualTo(3);
        String assignedHistory = json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText();
        String unassignedHistory = json(get(c.activity() + "/history").param("threadId", "" + thread), c.token(), null).get("text").asText();
        assertThat(assignedHistory).contains("選取的觀察").doesNotContain("沒有試法的觀察");
        assertThat(unassignedHistory).contains("未指定行動", "沒有試法的觀察");
        json(post(c.activity() + "/organizations"), c.token(), commit(preview(c, "HISTORY", selected), true));
        assertThat(records.countByExperimentId(c.id())).isEqualTo(1);
        assertThat(json(get(c.activity()), c.token(), null).get("records").get(0).get("discovery").asText()).isEqualTo("保留的觀察");
    }

    @Test void cloneAndFavoritesKeepIdentityAndDoNotCopyDiscoveriesOrHistory() throws Exception {
        var c = context(); var state = focus(c, "原試法");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        discovery(c, thread, method, "原發現");
        var favorite = json(put(c.activity() + "/favorites"), c.token(), Map.of("methodIds", List.of(method), "expectedVersion", state.get("version").asLong()));
        var cloned = json(post(c.activity() + "/methods"), c.token(), Map.of("threadId", thread, "content", "新試法", "sourceMethodId", method, "expectedVersion", 1));
        assertThat(cloned.get("id").asLong()).isNotEqualTo(method);
        assertThat(cloned.get("discoveryCount").asLong()).isZero();
        assertThat(cloned.get("hasHistory").asBoolean()).isFalse();
        assertThat(json(get(c.activity()), c.token(), null).get("favoriteMethods").get(0).get("id").asLong()).isEqualTo(method);
        send(post(c.activity() + "/methods"), c.token(), Map.of("threadId", thread, "content", " 原試法 ")).andExpect(status().isConflict());
        send(post(c.activity() + "/methods"), c.token(), Map.of("threadId", thread, "content", "原試法", "sourceMethodId", method, "expectedVersion", 1)).andExpect(status().isBadRequest());
        send(put(c.activity() + "/favorites"), c.token(), Map.of("methodIds", List.of(method, method), "expectedVersion", favorite.get("version").asLong())).andExpect(status().isBadRequest());
    }

    @Test void cardOutputUsesOneTransactionAndProtectsConcurrentBodyEdits() throws Exception {
        var c = context(); var state = focus(c, "卡片試法");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var finding = discovery(c, thread, method, "原始發現");
        var groups = List.of(group(thread, method, List.of(finding.get("id").asLong()), false, true));
        var request = commit(preview(c, "CARD", groups), false);
        request.put("newCard", Map.of("type", "note", "title", "整理卡片", "content", "整理文字"));
        long card = json(post(c.activity() + "/organizations"), c.token(), request).get("cardId").asLong();
        var appendPreview = json(post(c.activity() + "/organizations/preview"), c.token(), Map.of("mode", "CARD", "groups", groups, "targetCardId", card));
        json(put("/api/cards/" + card), c.token(), Map.of("type", "note", "title", "整理卡片", "content", "另一分頁的新正文", "isArchived", false, "expectedContentVersion", 0));
        var append = commit(appendPreview, true); append.put("cardContent", "本次追加");
        send(post(c.activity() + "/organizations"), c.token(), append).andExpect(status().isConflict());
        assertThat(records.countByExperimentId(c.id())).isEqualTo(1);
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText()).isEmpty();
        appendPreview = json(post(c.activity() + "/organizations/preview"), c.token(), Map.of("mode", "CARD", "groups", groups, "targetCardId", card));
        append = commit(appendPreview, true); append.put("cardContent", "本次追加");
        json(post(c.activity() + "/organizations"), c.token(), append);
        json(post(c.activity() + "/organizations"), c.token(), append);
        assertThat(json(get("/api/cards/" + card), c.token(), null).get("content").asText()).isEqualTo("另一分頁的新正文\n\n---\n\n本次追加");
        assertThat(records.countByExperimentId(c.id())).isZero();
        assertThat(json(get(c.root() + "/threads/" + thread), c.token(), null).get("cards").size()).isEqualTo(1);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void nextActionCopyAndUpdateAreAtomicAndVersioned() throws Exception {
        var c = context(); var state = focus(c, "原行動");
        long original = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        discovery(c, thread, original, "原發現保留");
        var copy = new LinkedHashMap<String, Object>();
        copy.put("threadId", thread); copy.put("methodId", original); copy.put("newContent", "另一種行動");
        copy.put("action", "COPY"); copy.put("expectedVersion", 1); copy.put("expectedMethodVersion", 1);
        var copied = json(put(c.activity() + "/focus"), c.token(), copy);
        long next = copied.get("currentMethod").get("id").asLong();
        assertThat(next).isNotEqualTo(original);
        assertThat(copied.get("currentMethod").get("discoveryCount").asLong()).isZero();
        assertThat(records.countByExperimentId(c.id())).isEqualTo(1);
        var update = new LinkedHashMap<String, Object>();
        update.put("threadId", thread); update.put("methodId", next); update.put("newContent", "修訂行動");
        update.put("action", "UPDATE"); update.put("expectedVersion", 1); update.put("expectedMethodVersion", 0);
        send(put(c.activity() + "/focus"), c.token(), update).andExpect(status().isConflict());
        assertThat(json(get(c.activity() + "/methods/" + next), c.token(), null).get("content").asText()).isEqualTo("另一種行動");
        update.put("expectedVersion", 2);
        update.put("favoriteMethodIds", List.of(next, next));
        send(put(c.activity() + "/focus"), c.token(), update).andExpect(status().isBadRequest());
        assertThat(json(get(c.activity() + "/methods/" + next), c.token(), null).get("content").asText()).isEqualTo("另一種行動");
        update.put("favoriteMethodIds", List.of(next));
        var updated = json(put(c.activity() + "/focus"), c.token(), update);
        assertThat(updated.get("currentTry").asText()).isEqualTo("修訂行動");
        assertThat(updated.get("favoriteMethods").get(0).get("id").asLong()).isEqualTo(next);
        copy.put("newContent", "原行動"); copy.put("expectedVersion", updated.get("version").asLong());
        send(put(c.activity() + "/focus"), c.token(), copy).andExpect(status().isBadRequest());
        assertThat(json(get(c.activity()), c.token(), null).get("currentMethod").get("id").asLong()).isEqualTo(next);
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void discoveryContentAndCrossThreadAssignmentSaveTogetherWithoutChangingNextAction() throws Exception {
        var c = context(); var state = focus(c, "來源行動");
        long source = state.get("currentMethod").get("id").asLong(), sourceThread = state.get("currentThreadId").asLong();
        var finding = discovery(c, sourceThread, source, "原觀察");
        var occurredAt = records.findById(finding.get("id").asLong()).orElseThrow().getOccurredAt().toInstant();
        long targetThread = json(post(c.root() + "/threads"), c.token(), Map.of("name", "另一方向")).get("id").asLong();
        long target = json(post(c.activity() + "/methods"), c.token(), Map.of("threadId", targetThread, "content", "目標行動")).get("id").asLong();
        var body = new LinkedHashMap<String, Object>();
        body.put("threadId", targetThread); body.put("methodId", target); body.put("discovery", "同時修正的觀察");
        body.put("expectedVersion", 0); body.put("expectedMethodVersion", 99);
        var endpoint = c.activity() + "/records/" + finding.get("id").asLong();
        send(put(endpoint), c.token(), body).andExpect(status().isConflict());
        assertThat(records.findById(finding.get("id").asLong()).orElseThrow().getDiscovery()).isEqualTo("原觀察");
        body.put("expectedMethodVersion", 0);
        var saved = json(put(endpoint), c.token(), body);
        assertThat(saved.get("threadId").asLong()).isEqualTo(targetThread);
        assertThat(saved.get("methodId").asLong()).isEqualTo(target);
        assertThat(saved.get("discovery").asText()).isEqualTo("同時修正的觀察");
        assertThat(java.time.ZonedDateTime.parse(saved.get("occurredAt").asText()).toInstant()).isEqualTo(occurredAt);
        assertThat(json(get(c.activity()), c.token(), null).get("currentMethod").get("id").asLong()).isEqualTo(source);
        send(put(endpoint), c.token(), body).andExpect(status().isConflict());
    }

    @Test @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void organizeAndAdoptRejectStaleFocusBeforeClearingAnything() throws Exception {
        var c = context(); var state = focus(c, "待整理行動");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        discovery(c, thread, method, "舊觀察");
        var preview = preview(c, "UPDATE_METHOD", List.of(group(thread, method, List.of(), true, true)));
        var request = commit(preview, true); request.put("newMethodContent", "整理後行動"); request.put("expectedFocusVersion", 1);
        json(put(c.activity() + "/focus"), c.token(), Map.of("threadId", thread, "expectedVersion", 1));
        send(post(c.activity() + "/organizations"), c.token(), request).andExpect(status().isConflict());
        assertThat(records.countByExperimentId(c.id())).isEqualTo(1);
        assertThat(json(get(c.activity() + "/methods/" + method), c.token(), null).get("content").asText()).isEqualTo("待整理行動");
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread).param("methodId", "" + method), c.token(), null).get("text").asText()).isEmpty();
        request.put("expectedFocusVersion", 2);
        json(post(c.activity() + "/organizations"), c.token(), request);
        assertThat(records.countByExperimentId(c.id())).isZero();
        assertThat(json(get(c.activity()), c.token(), null).get("currentTry").asText()).isEqualTo("整理後行動");
        json(post(c.activity() + "/organizations"), c.token(), request);
        assertThat(json(get(c.activity()), c.token(), null).get("version").asLong()).isEqualTo(3);
    }

    @Test void ownershipAndCrossExperimentAssignmentsAreRejectedAndMethodsHistorySurvivesDeletion() throws Exception {
        var c = context(); var other = context(); var state = focus(c, "本人試法"); var foreign = focus(other, "他人試法");
        long method = state.get("currentMethod").get("id").asLong(), thread = state.get("currentThreadId").asLong();
        var finding = discovery(c, thread, method, "本人發現");
        send(put(c.activity() + "/records/" + finding.get("id").asLong() + "/assignment"), c.token(),
            Map.of("threadId", foreign.get("currentThreadId").asLong(), "expectedVersion", 0)).andExpect(status().isNotFound());
        send(get(c.activity()), other.token(), null).andExpect(status().isNotFound());
        var p = preview(c, "HISTORY", List.of(group(thread, method, List.of(finding.get("id").asLong()), false, true)));
        json(post(c.activity() + "/organizations"), c.token(), commit(p, false));
        send(delete(c.activity() + "/methods/" + method).param("expectedVersion", "2"), c.token(), null).andExpect(status().isOk());
        var remaining = json(get(c.activity()), c.token(), null).get("records").get(0);
        assertThat(remaining.get("methodId").isNull()).isTrue();
        assertThat(json(get(c.activity() + "/history").param("threadId", "" + thread), c.token(), null).get("text").asText()).contains("本人試法", "本人發現");
    }
}
