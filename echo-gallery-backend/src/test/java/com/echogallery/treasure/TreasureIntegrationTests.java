package com.echogallery.treasure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.card.CardRepository;
import com.echogallery.demo.DemoSessionService;
import com.echogallery.experiment.ExperimentRepository;
import com.echogallery.issue.*;
import com.echogallery.tag.TagRepository;
import com.echogallery.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class TreasureIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired Clock clock;
    @Autowired TreasureRepository treasures;
    @Autowired TreasureTrialRepository trials;
    @Autowired TreasureTrialAttemptRepository attempts;
    @Autowired TreasureYieldRepository yields;
    @Autowired TreasureRelationRepository relations;
    @Autowired TreasureLifecycle lifecycle;
    @Autowired DemoSessionService demo;
    @Autowired UserRepository users;
    @Autowired CardRepository cards;
    @Autowired ExperimentRepository experiments;
    @Autowired TagRepository tags;
    @Autowired IssueRepository issues;
    @Autowired IssueCardRepository issueCards;
    @Autowired IssueUpdateRepository issueUpdates;
    @Autowired TransactionTemplate transactions;
    private final Set<Long> owners = new HashSet<>();

    @AfterEach
    void cleanOwnData() {
        transactions.executeWithoutResult(status -> {
            for (Long owner : owners) {
                if (!users.existsById(owner)) continue;
                lifecycle.clearOwner(owner);
                experiments.deleteAll(experiments.findAllByUserId(owner));
                experiments.flush();
                for (Issue issue : issues.findAllByUserId(owner)) {
                    issueUpdates.deleteByIssueId(issue.getId());
                    issueCards.deleteByIssueId(issue.getId());
                }
                issues.deleteAll(issues.findAllByUserId(owner));
                issues.flush();
                cards.deleteAll(cards.findAllByUserId(owner));
                cards.flush();
                tags.deleteAll(tags.findAllByUserId(owner));
                tags.flush();
                users.deleteById(owner);
                users.flush();
            }
        });
    }

    @Test
    void listIncludesBoundedUnicodeDescriptionWithoutChangingFullDetail() throws Exception {
        String token = register();
        Map<String, Object> data = content("有簡介的成果", List.of());
        data.put("description", "🙂".repeat(200));
        long id = json(send(post("/api/treasures"), token, data).andExpect(status().isOk())).get("id").asLong();
        send(get("/api/treasures"), token, null).andExpect(jsonPath("$.content[0].description").value("🙂".repeat(160) + "…"));
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.description").value("🙂".repeat(200)));
    }

    @Test
    void listOrdersByLatestActivityAcrossFiltersPagesAndArchive() throws Exception {
        String token = register();
        long first = create(token, "排序成果一", List.of());
        long second = create(token, "排序成果二", List.of());
        long third = create(token, "排序成果三", List.of());
        ZonedDateTime older = ZonedDateTime.now(clock).minusDays(2);
        transactions.executeWithoutResult(status -> {
            for (long id : List.of(first, second, third)) {
                treasures.findById(id).orElseThrow().setUpdatedAt(older);
            }
        });
        send(get("/api/treasures"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.content[2].id").value(first));

        send(put("/api/treasures/{id}", first), token, content("排序成果一已編輯", List.of()))
                .andExpect(status().isOk());
        send(get("/api/treasures").param("keyword", "排序成果").param("rank", "STONE").param("size", "1"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.totalElements").value(3));
        send(get("/api/treasures").param("size", "1").param("page", "1"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(third));
        send(post("/api/treasures/{id}/yields", second), token, yieldPayload(LocalDate.now(clock)))
                .andExpect(status().isOk());
        send(get("/api/treasures"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(second));

        send(put("/api/treasures/{id}/archive", first), token, null).andExpect(status().isOk());
        send(put("/api/treasures/{id}/archive", second), token, null).andExpect(status().isOk());
        transactions.executeWithoutResult(status -> treasures.findById(first).orElseThrow().setUpdatedAt(older));
        send(post("/api/treasures/{id}/yields", first), token, yieldPayload(LocalDate.now(clock)))
                .andExpect(status().isOk());
        send(get("/api/treasures").param("archived", "true"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(first))
                .andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/treasures"), token, null)
                .andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void collectsFiltersEditsArchivesAndDeletesWithoutDeletingSource() throws Exception {
        String token = register();
        long source = create(token, "來源成果", List.of());
        long id = create(token, "新整合成果", List.of(source));
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("STONE"))
                .andExpect(jsonPath("$.sourceTreasures[0].id").value(source));
        send(get("/api/treasures").param("keyword", "整合").param("rank", "STONE").param("size", "200"), token, null)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.size").value(20));
        send(get("/api/treasures/{id}/relations", source), token, null).andExpect(jsonPath("$.content[0].id").value(id));
        send(put("/api/treasures/{id}", id), token, content("更新成果", List.of(source)))
                .andExpect(jsonPath("$.title").value("更新成果"));
        send(put("/api/treasures/{id}/archive", id), token, null).andExpect(status().isOk());
        send(get("/api/treasures/stats"), token, null).andExpect(jsonPath("$.total").value(1));
        send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isConflict());
        send(put("/api/treasures/{id}/restore", id), token, null).andExpect(status().isOk());
        send(delete("/api/treasures/{id}", id), token, null).andExpect(status().isNoContent());
        send(get("/api/treasures/{id}", source), token, null).andExpect(status().isOk());
        assertThat(relations.count()).isZero();
    }

    @Test
    void requiresRealResultsPreservesFailureAndPromotesExactlyOnceThroughAllRanks() throws Exception {
        String token = register();
        long id = create(token, "完整作品", List.of());
        long bronze = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isConflict());
        send(post("/api/treasures/{id}/trials/{trialId}/attempts", id, bronze), token,
                Map.of("status", "NOT_PASSED", "resultNote", "第一次未通過", "attemptedAt", time())).andExpect(status().isOk());
        var edited = trial("PLANNED", null); edited.put("criteria", "新的具體條件");
        send(put("/api/treasures/{id}/trials/{trialId}", id, bronze), token, edited).andExpect(status().isOk());
        send(get("/api/treasures/{id}/trials/{trialId}/attempts", id, bronze), token, null)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].criteriaSnapshot").value("完成並留下證據"))
                .andExpect(jsonPath("$.content[0].resultNote").value("第一次未通過"));
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("STONE"));
        for (String rank : List.of("BRONZE", "SILVER", "GOLD")) {
            long trialId = rank.equals("BRONZE") ? bronze : json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
            send(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId), token, Map.of("resultNote", "", "attemptedAt", time())).andExpect(status().isBadRequest());
            send(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId), token, result()).andExpect(status().isOk());
            send(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId), token, result()).andExpect(status().isOk());
            send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value(rank));
            send(put("/api/treasures/{id}/trials/{trialId}", id, trialId), token, trial("NOT_PASSED", time())).andExpect(status().isConflict());
        }
        send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isBadRequest());
        send(get("/api/treasures/{id}/challenge-route", id), token, null).andExpect(jsonPath("$.length()").value(3));
        send(get("/api/treasures/{id}/trials/{trialId}/attempts", id, bronze), token, null).andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("GOLD")).andExpect(jsonPath("$.trialCount").value(3));
    }

    @Test
    void enforcesOwnershipOnEveryResourceAndSource() throws Exception {
        String token = register();
        String other = register();
        long id = create(token, "私人成果", List.of());
        long child = create(other, "另一人的成果", List.of());
        long trial = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        long yield = json(send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk())).get("id").asLong();
        send(get("/api/treasures/{id}", id), other, null).andExpect(status().isNotFound());
        send(put("/api/treasures/{id}", id), other, content("越權", List.of())).andExpect(status().isNotFound());
        send(delete("/api/treasures/{id}", id), other, null).andExpect(status().isNotFound());
        send(put("/api/treasures/{id}/archive", id), other, null).andExpect(status().isNotFound());
        send(get("/api/treasures/{id}/relations", id), other, null).andExpect(status().isNotFound());
        send(get("/api/treasures/{id}/trials", id), other, null).andExpect(status().isNotFound());
        send(get("/api/treasures/{id}/challenge-route", id), other, null).andExpect(status().isNotFound());
        send(get("/api/treasures/{id}/trials/{trialId}/attempts", id, trial), other, null).andExpect(status().isNotFound());
        send(post("/api/treasures/{id}/trials/{trialId}/attempts", id, trial), other,
                Map.of("status", "NOT_PASSED", "resultNote", "不可越權寫入", "attemptedAt", time())).andExpect(status().isNotFound());
        send(get("/api/treasures/{id}/yields", id), other, null).andExpect(status().isNotFound());
        send(post("/api/treasures/{id}/trials/{trialId}/pass", child, trial), other, result()).andExpect(status().isNotFound());
        send(put("/api/treasures/{id}/yields/{yieldId}", child, yield), other, yieldPayload(LocalDate.now(clock))).andExpect(status().isNotFound());
        send(delete("/api/treasures/{id}/yields/{yieldId}", child, yield), other, null).andExpect(status().isNotFound());
        send(post("/api/treasures"), other, content("跨 owner 來源", List.of(id))).andExpect(status().isNotFound());
        send(get("/api/treasures"), other, null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/treasures/stats"), other, null).andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void rejectsCyclesSelfReferencesAndDuplicatesWithoutLosingExistingSources() throws Exception {
        String token = register();
        long a = create(token, "A", List.of());
        long b = create(token, "B", List.of(a));
        long c = create(token, "C", List.of(b));
        send(put("/api/treasures/{id}", a), token, content("循環", List.of(c))).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("此來源會造成循環關聯"));
        send(put("/api/treasures/{id}", b), token, content("自我", List.of(b))).andExpect(status().isBadRequest());
        send(put("/api/treasures/{id}", b), token, content("重複", List.of(a, a))).andExpect(status().isBadRequest());
        send(get("/api/treasures/{id}", b), token, null).andExpect(jsonPath("$.title").value("B")).andExpect(jsonPath("$.sourceTreasures[0].id").value(a));
        send(delete("/api/treasures/{id}", b), token, null).andExpect(status().isNoContent());
        send(get("/api/treasures/{id}", c), token, null).andExpect(jsonPath("$.sourceTreasures.length()").value(0));
    }

    @Test
    void concurrentPassRequestsDoNotDoublePromote() throws Exception {
        String token = register();
        long id = create(token, "併發成果", List.of());
        long trialId = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        String payload = mapper.writeValueAsString(result());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> pass = () -> mvc.perform(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(pass);
            Future<Integer> second = executor.submit(pass);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("BRONZE"));
    }

    @Test
    void concurrentOppositeRelationsCannotCreateACycle() throws Exception {
        String token = register();
        long a = create(token, "併發 A", List.of());
        long b = create(token, "併發 B", List.of());
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> firstRequest = () -> send(put("/api/treasures/{id}", a), token, content("A", List.of(b))).andReturn().getResponse().getStatus();
            Callable<Integer> secondRequest = () -> send(put("/api/treasures/{id}", b), token, content("B", List.of(a))).andReturn().getResponse().getStatus();
            Future<Integer> first = executor.submit(firstRequest);
            Future<Integer> second = executor.submit(secondRequest);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(relations.count()).isEqualTo(1);
    }

    @Test
    void rejectsFutureEventsUnsafeUrlsAndForeignCardOrExperimentSources() throws Exception {
        String token = register();
        String other = register();
        Map<String, Object> data = content("不合法的成果", List.of());
        data.put("acquiredOn", LocalDate.now(clock).plusDays(1).toString());
        send(post("/api/treasures"), token, data).andExpect(status().isBadRequest());
        data.put("acquiredOn", LocalDate.now(clock).toString());
        data.put("artifactUrl", "javascript:alert(1)");
        send(post("/api/treasures"), token, data).andExpect(status().isBadRequest());
        data.remove("artifactUrl"); data.put("rank", "GOLD");
        long id = json(send(post("/api/treasures"), token, data).andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value("STONE"))).get("id").asLong();
        long trialId = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId), token,
                Map.of("resultNote", "未發生的挑戰", "attemptedAt", ZonedDateTime.now(clock).plusDays(1).toString())).andExpect(status().isBadRequest());
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("STONE"))
                .andExpect(jsonPath("$.activeTrial.status").value("PLANNED"));
        long card = json(send(post("/api/cards"), other, Map.of("type", "note", "title", "私人來源", "cardNote", "不可跨 owner")).andExpect(status().isOk())).get("id").asLong();
        long experiment = json(send(post("/api/experiments"), other, Map.of("title", "私人實驗")).andExpect(status().isOk())).get("id").asLong();
        data.remove("rank"); data.put("sourceCardId", card);
        send(post("/api/treasures"), token, data).andExpect(status().isNotFound());
        data.remove("sourceCardId"); data.put("sourceExperimentId", experiment);
        send(post("/api/treasures"), token, data).andExpect(status().isNotFound());
    }

    @Test
    void plansFutureChallengesAndListsOnlyExecutableChallengeWithYieldCounts() throws Exception {
        String token = register();
        long id = create(token, "有成長路線的成果", List.of());
        var future = new HashMap<>(trial("PLANNED", null));
        future.put("targetRank", "SILVER");
        long silver = json(send(post("/api/treasures/{id}/trials", id), token, future).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/trials/{trialId}/pass", id, silver), token, result()).andExpect(status().isConflict());
        send(post("/api/treasures/{id}/trials/{trialId}/attempts", id, silver), token,
                Map.of("status", "ATTEMPTED", "resultNote", "不能提前嘗試", "attemptedAt", time())).andExpect(status().isConflict());
        long bronze = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").doesNotExist()).andExpect(jsonPath("$.externalUrl").doesNotExist());
        send(get("/api/treasures").param("keyword", "成長路線"), token, null)
                .andExpect(jsonPath("$.content[0].activeTrial.id").value(bronze))
                .andExpect(jsonPath("$.content[0].yieldCount").value(1));
        send(post("/api/treasures/{id}/trials/{trialId}/pass", id, bronze), token, result()).andExpect(status().isOk());
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.activeTrial.id").value(silver));
        send(post("/api/treasures/{id}/trials/{trialId}/pass", id, silver), token, result()).andExpect(status().isOk());
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.rank").value("SILVER"));
    }

    @Test
    void yieldCardRelationChecksOwnershipAndDetachesOnCardDelete() throws Exception {
        String token = register();
        String other = register();
        long id = create(token, "保留產出", List.of());
        long card = json(send(post("/api/cards"), token, Map.of("type", "note", "title", "讀者回應", "cardNote", "實際回饋")).andExpect(status().isOk())).get("id").asLong();
        var data = new HashMap<>(yieldPayload(LocalDate.now(clock))); data.put("relatedCardId", card);
        long otherTreasure = create(other, "別人成果", List.of());
        send(post("/api/treasures/{id}/yields", otherTreasure), other, data).andExpect(status().isNotFound());
        send(post("/api/treasures/{id}/yields", id), token, data).andExpect(status().isOk()).andExpect(jsonPath("$.relatedCard.id").value(card));
        send(get("/api/treasures/{id}/yields", id), token, null).andExpect(jsonPath("$.content[0].relatedCard.id").value(card));
        send(delete("/api/cards/{id}", card), token, null).andExpect(status().isOk());
        send(get("/api/treasures/{id}/yields", id), token, null).andExpect(jsonPath("$.content[0].relatedCard").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void currentActionsOnlyIncludeNextChallengesAndNeverCountPlannedTrialsAsEvents() throws Exception {
        String token = register();
        long first = create(token, "進行中的試煉", List.of());
        long second = create(token, "多次收益的成果", List.of());
        long third = create(token, "另一件有收益的成果", List.of());
        send(post("/api/treasures/{id}/trials", first), token, trial("PLANNED", null)).andExpect(status().isOk());
        for (int index = 0; index < 7; index++) send(post("/api/treasures/{id}/yields", second), token, yieldPayload(LocalDate.now(clock).minusDays(1))).andExpect(status().isOk());
        send(post("/api/treasures/{id}/yields", third), token, yieldPayload(LocalDate.now(clock).minusDays(2))).andExpect(status().isOk());
        send(get("/api/overview/current"), token, null).andExpect(jsonPath("$.treasureActions.length()").value(1))
                .andExpect(jsonPath("$.treasureActions[0].id").value(first))
                .andExpect(jsonPath("$.treasureActions[0].activeTrial.targetRank").value("BRONZE"))
                .andExpect(jsonPath("$.treasureActions[0].yieldCount").value(0));
        JsonNode recent = json(send(get("/api/overview/recent"), token, null).andExpect(status().isOk()));
        for (JsonNode event : recent.get("period").get("recentTreasureProgress")) assertThat(event.get("kind").asText()).isNotEqualTo("TRIAL_ATTEMPTED");
        assertThat(recent.get("period").get("treasureGrowth").get("yieldTreasureCount").asLong()).isEqualTo(2);
    }

    @Test
    void recentYieldsAndProgressHaveIndependentLimitsAndDoNotHideOlderAttempts() throws Exception {
        String token = register();
        long id = create(token, "有多筆價值的成果", List.of());
        long trialId = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null))
                .andExpect(status().isOk())).get("id").asLong();
        for (int index = 1; index <= 3; index++) {
            send(post("/api/treasures/{id}/trials/{trialId}/attempts", id, trialId), token,
                    Map.of("status", "NOT_PASSED", "resultNote", "保留嘗試" + index,
                            "attemptedAt", ZonedDateTime.now(clock).minusDays(index).toString())).andExpect(status().isOk());
        }
        for (int index = 0; index < 7; index++) {
            send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());
        }
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock).minusDays(45))).andExpect(status().isOk());
        String other = register();
        long hidden = create(other, "其他人的成果", List.of());
        send(post("/api/treasures/{id}/yields", hidden), other, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());

        JsonNode period = json(send(get("/api/overview/recent"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.period.recentTreasureYields.length()").value(3))
                .andExpect(jsonPath("$.period.recentTreasureProgress.length()").value(3))
                .andExpect(jsonPath("$.period.recentTreasureProgress[0].kind").value("COLLECTED"))
                .andExpect(jsonPath("$.period.recentTreasureProgress[1].kind").value("TRIAL_ATTEMPTED"))
                .andExpect(jsonPath("$.period.recentTreasureProgress[2].kind").value("TRIAL_ATTEMPTED"))
                .andExpect(jsonPath("$.period.treasureGrowth.yieldTreasureCount").value(1))
                .andExpect(jsonPath("$.period.treasureGrowth.yieldRecordCount").value(7))
                .andExpect(jsonPath("$.period.recentTreasureEvents").doesNotExist())).get("period");
        for (JsonNode event : period.get("recentTreasureYields")) {
            assertThat(event.get("kind").asText()).isEqualTo("YIELD");
            assertThat(event.get("treasureId").asLong()).isEqualTo(id);
        }
        for (JsonNode event : period.get("recentTreasureProgress")) assertThat(event.get("treasureId").asLong()).isEqualTo(id);
    }

    @Test
    void currentActionsIncludeRetriesButDoNotPresentFuturePlansOrOtherOwnersAsCurrentChallenges() throws Exception {
        String token = register();
        create(token, "較早入藏", List.of());
        long retry = create(token, "可以重試", List.of());
        long trialId = json(send(post("/api/treasures/{id}/trials", retry), token, trial("PLANNED", null))
                .andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/trials/{trialId}/attempts", retry, trialId), token,
                Map.of("status", "NOT_PASSED", "resultNote", "再試一次", "attemptedAt", time())).andExpect(status().isOk());
        send(post("/api/treasures/{id}/yields", retry), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());
        create(token, "最近入藏", List.of());
        long future = create(token, "只規劃金級", List.of());
        send(post("/api/treasures/{id}/trials", future), token,
                Map.of("targetRank", "GOLD", "title", "未來才做", "criteria", "未來條件")).andExpect(status().isOk());
        long archived = create(token, "已封存", List.of());
        send(put("/api/treasures/{id}/archive", archived), token, null).andExpect(status().isOk());
        String other = register();
        long hidden = create(other, "別人的成果", List.of());
        send(post("/api/treasures/{id}/trials", hidden), other, trial("PLANNED", null)).andExpect(status().isOk());

        send(get("/api/overview/current"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.treasureCount").value(4))
                .andExpect(jsonPath("$.treasureActions.length()").value(1))
                .andExpect(jsonPath("$.treasureActions[0].id").value(retry))
                .andExpect(jsonPath("$.treasureActions[0].activeTrial.status").value("NOT_PASSED"))
                .andExpect(jsonPath("$.treasureActions[0].yieldCount").value(1));
    }

    @Test
    void currentActionsUseLastChallengeUpdateAndLimitToThreeRatherThanFillingFromCollections() throws Exception {
        String token = register();
        List<Long> ids = new ArrayList<>();
        List<Long> trialIds = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            long id = create(token, "有方向的成果" + index, List.of());
            ids.add(id);
            long trialId = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null))
                    .andExpect(status().isOk())).get("id").asLong();
            trialIds.add(trialId);
            ZonedDateTime updatedAt = ZonedDateTime.now(clock).minusHours(10 - index);
            transactions.executeWithoutResult(status -> trials.findById(trialId).orElseThrow().setUpdatedAt(updatedAt));
        }
        create(token, "最近入藏但沒有方向", List.of());
        send(put("/api/treasures/{id}/trials/{trialId}", ids.get(0), trialIds.get(0)), token,
                Map.of("title", "重新留下的方向", "criteria", "邀請真實使用者試用並留下回饋")).andExpect(status().isOk());

        send(get("/api/overview/current"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.treasureCount").value(5))
                .andExpect(jsonPath("$.treasureActions.length()").value(3))
                .andExpect(jsonPath("$.treasureActions[0].id").value(ids.get(0)))
                .andExpect(jsonPath("$.treasureActions[0].activeTrial.criteria").value("邀請真實使用者試用並留下回饋"))
                .andExpect(jsonPath("$.treasureActions[1].id").value(ids.get(3)))
                .andExpect(jsonPath("$.treasureActions[2].id").value(ids.get(2)));
    }

    @Test
    void goldTreasureRemainsInCollectionButDoesNotBecomeAnActionWithoutAnotherChallenge() throws Exception {
        String token = register();
        long id = create(token, "金級珍藏", List.of());
        for (String rank : List.of("BRONZE", "SILVER", "GOLD")) {
            long trialId = json(send(post("/api/treasures/{id}/trials", id), token,
                    Map.of("targetRank", rank, "title", "確認外部價值", "criteria", "取得可確認的結果"))
                    .andExpect(status().isOk())).get("id").asLong();
            send(post("/api/treasures/{id}/trials/{trialId}/pass", id, trialId), token, result()).andExpect(status().isOk());
        }
        send(get("/api/overview/current"), token, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.treasureCount").value(1)).andExpect(jsonPath("$.treasureActions.length()").value(0));
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());
    }

    @Test
    void countsYieldTreasuresSeparatelyFromRecordsAndUsesActualDates() throws Exception {
        String token = register();
        long id = create(token, "多次使用", List.of());
        LocalDate recent = LocalDate.now(clock).minusDays(1);
        long yieldId = json(send(post("/api/treasures/{id}/yields", id), token, yieldPayload(recent)).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(recent)).andExpect(status().isOk());
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(recent.minusDays(100))).andExpect(status().isOk());
        send(get("/api/overview/recent"), token, null).andExpect(jsonPath("$.period.treasureGrowth.yieldTreasureCount").value(1))
                .andExpect(jsonPath("$.period.treasureGrowth.yieldRecordCount").value(2)).andExpect(jsonPath("$.period.closure").doesNotExist());
        send(put("/api/treasures/{id}/yields/{yieldId}", id, yieldId), token, yieldPayload(recent.minusDays(100))).andExpect(status().isOk());
        send(get("/api/overview/recent"), token, null).andExpect(jsonPath("$.period.treasureGrowth.yieldRecordCount").value(1));
        send(delete("/api/treasures/{id}/yields/{yieldId}", id, yieldId), token, null).andExpect(status().isNoContent());
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock).plusDays(1))).andExpect(status().isBadRequest());
        send(put("/api/treasures/{id}/archive", id), token, null).andExpect(status().isOk());
        send(get("/api/overview/current"), token, null).andExpect(jsonPath("$.treasureCount").value(0)).andExpect(jsonPath("$.treasureActions.length()").value(0)).andExpect(jsonPath("$.nextSteps").doesNotExist());
        send(get("/api/overview/recent"), token, null).andExpect(jsonPath("$.period.treasureGrowth.yieldRecordCount").value(1));
    }

    @Test
    void archivedTreasureAcceptsNewYieldWithoutRestoringOrBypassingOwnership() throws Exception {
        String token = register();
        String other = register();
        long id = create(token, "已封存但仍有回應的成果", List.of());
        send(put("/api/treasures/{id}/archive", id), token, null).andExpect(status().isOk());
        long yieldId = json(send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock)))
                .andExpect(status().isOk())).get("id").asLong();
        send(get("/api/treasures/{id}/yields", id), token, null)
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(yieldId));
        send(get("/api/treasures/{id}", id), token, null)
                .andExpect(jsonPath("$.isArchived").value(true)).andExpect(jsonPath("$.yieldCount").value(1));
        send(post("/api/treasures/{id}/yields", id), other, yieldPayload(LocalDate.now(clock)))
                .andExpect(status().isNotFound());
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock).plusDays(1)))
                .andExpect(status().isBadRequest());
        send(put("/api/treasures/{id}/yields/{yieldId}", id, yieldId), token, yieldPayload(LocalDate.now(clock)))
                .andExpect(status().isConflict());
        send(delete("/api/treasures/{id}/yields/{yieldId}", id, yieldId), token, null).andExpect(status().isConflict());
        send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isConflict());
        send(get("/api/overview/current"), token, null)
                .andExpect(jsonPath("$.treasureCount").value(0)).andExpect(jsonPath("$.treasureActions.length()").value(0));
        send(put("/api/treasures/{id}/restore", id), token, null).andExpect(status().isOk());
        send(delete("/api/treasures/{id}/yields/{yieldId}", id, yieldId), token, null).andExpect(status().isNoContent());
        send(get("/api/treasures/{id}/yields", id), token, null).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deletingCardAndExperimentDetachesSourcesAndPreservesTreasureAndYield() throws Exception {
        String token = register();
        long card = json(send(post("/api/cards"), token, Map.of("type", "note", "title", "來源卡片", "cardNote", "筆記")).andExpect(status().isOk())).get("id").asLong();
        long experiment = json(send(post("/api/experiments"), token, Map.of("title", "來源實驗")).andExpect(status().isOk())).get("id").asLong();
        Map<String, Object> content = content("保留的成果", List.of());
        content.put("sourceCardId", card); content.put("sourceExperimentId", experiment);
        long id = json(send(post("/api/treasures"), token, content).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());
        send(delete("/api/cards/{id}", card), token, null).andExpect(status().isOk());
        send(delete("/api/experiments/{id}", experiment), token, null).andExpect(status().isNoContent());
        send(get("/api/treasures/{id}", id), token, null).andExpect(jsonPath("$.sourceCard").doesNotExist()).andExpect(jsonPath("$.sourceExperiment").doesNotExist()).andExpect(jsonPath("$.yieldCount").value(1));
    }

    @Test
    void demoResetAndExpiryCleanTreasureSourcesTrialsYieldsAndExperiments() throws Exception {
        JsonNode session = json(send(post("/api/auth/demo-sessions"), null, Map.of("library", "tech")).andExpect(status().isOk()));
        String token = session.get("token").asText();
        long owner = session.get("id").asLong(); owners.add(owner);
        long card = cards.findAllByUserId(owner).getFirst().getId();
        long experiment = json(send(post("/api/experiments"), token, Map.of("title", "Demo 實驗")).andExpect(status().isOk())).get("id").asLong();
        Map<String, Object> content = content("Demo 成果", List.of()); content.put("sourceCardId", card); content.put("sourceExperimentId", experiment);
        long source = json(send(post("/api/treasures"), token, content).andExpect(status().isOk())).get("id").asLong();
        long id = create(token, "Demo 衍生", List.of(source));
        long demoTrial = json(send(post("/api/treasures/{id}/trials", id), token, trial("PLANNED", null)).andExpect(status().isOk())).get("id").asLong();
        send(post("/api/treasures/{id}/trials/{trialId}/attempts", id, demoTrial), token,
                Map.of("status", "NOT_PASSED", "resultNote", "Demo 嘗試", "attemptedAt", time())).andExpect(status().isOk());
        send(post("/api/treasures/{id}/yields", id), token, yieldPayload(LocalDate.now(clock))).andExpect(status().isOk());
        send(get("/api/treasures/{id}", id), token, null).andExpect(status().isOk());
        JsonNode reset = json(send(post("/api/auth/demo-sessions/reset"), token, null).andExpect(status().isOk()));
        assertThat(treasures.countByUserIdAndArchivedFalse(owner)).isZero();
        assertThat(experiments.findAllByUserId(owner)).isEmpty();
        token = reset.get("token").asText();
        create(token, "到期前成果", List.of());
        var user = users.findById(owner).orElseThrow(); user.setDemoExpiresAt(ZonedDateTime.now(clock).minusMinutes(1)); users.saveAndFlush(user);
        demo.clearExpiredSessions();
        assertThat(users.existsById(owner)).isFalse();
        assertThat(treasures.count()).isZero(); assertThat(trials.count()).isZero(); assertThat(attempts.count()).isZero(); assertThat(yields.count()).isZero(); assertThat(relations.count()).isZero();
    }

    private String register() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        JsonNode user = json(send(post("/api/auth/register"), null, Map.of("username", "treasure-" + suffix, "email", suffix + "@example.com", "password", "password123")).andExpect(status().isOk()));
        owners.add(user.get("id").asLong());
        return user.get("token").asText();
    }
    private long create(String token, String title, List<Long> sources) throws Exception { return json(send(post("/api/treasures"), token, content(title, sources)).andExpect(status().isOk())).get("id").asLong(); }
    private Map<String, Object> content(String title, List<Long> sources) { return new HashMap<>(Map.of("title", title, "acquiredOn", LocalDate.now(clock).toString(), "sourceTreasureIds", sources)); }
    private String time() { return ZonedDateTime.now(clock).minusMinutes(1).toString(); }
    private Map<String, Object> trial(String status, String attemptedAt) { var result = new HashMap<String, Object>(Map.of("title", "現實挑戰", "criteria", "完成並留下證據", "status", status, "resultNote", "已實際嘗試")); if (attemptedAt != null) result.put("attemptedAt", attemptedAt); return result; }
    private Map<String, Object> result() { return Map.of("resultNote", "已完成檢定條件", "attemptedAt", time()); }
    private Map<String, Object> yieldPayload(LocalDate date) { return Map.of("title", "真正再次使用", "occurredOn", date.toString()); }
    private ResultActions send(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }
    private JsonNode json(ResultActions result) throws Exception { return mapper.readTree(result.andReturn().getResponse().getContentAsString()); }
}
