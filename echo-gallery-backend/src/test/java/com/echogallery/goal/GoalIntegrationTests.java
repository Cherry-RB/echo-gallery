package com.echogallery.goal;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.card.*;
import com.echogallery.user.*;
import com.echogallery.demo.DemoSessionService;
import jakarta.persistence.EntityManager;
import tools.jackson.databind.*;

@AutoConfigureMockMvc @Transactional
class GoalIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager em;
    @Autowired CardRepository cards;
    @Autowired UserRepository users;
    @Autowired DemoSessionService demo;
    @Autowired Clock clock;
    @Autowired GoalService goals;

    @Test
    @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void failedReorderRollsBackEarlierWritesAndCleansItsIsolatedOwner() throws Exception {
        // 此案例需跨真實交易確認 rollback；資料只建立於 Testcontainers，最後清掉專屬 owner。
        var auth=session();String token=auth.get("token").asText();long owner=auth.get("id").asLong();Long cardId=null;
        try {
            var user=users.findById(owner).orElseThrow();
            var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("交易回滾測試來源").build());cardId=card.getId();
            long first=create(token,List.of()).get("id").asLong(),second=create(token,List.of()).get("id").asLong();
            create(token,List.of(source("CARD",cardId)));
            cards.deleteById(cardId);
            // 前兩個目標可調整，第三個目標的失效來源在交易中途驗證失敗。
            send(post("/api/goals/"+first+"/pin-order"),token,Map.of("direction","DOWN","version",1,"adjacentId",second,"adjacentVersion",1)).andExpect(status().isBadRequest());
            send(get("/api/goals/"+first),token,null).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.definition.pinOrder").value(0));
            send(get("/api/goals/"+first+"/revisions"),token,null).andExpect(jsonPath("$.length()").value(1));
        } finally {
            goals.clearOwner(owner);
            if(cardId!=null&&cards.existsById(cardId))cards.deleteById(cardId);
            users.deleteById(owner);
        }
        assertThat(users.existsById(owner)).isFalse();
    }

    @Test void multiStatusArchiveScopesAndLegacyQueriesStayConsistent() throws Exception {
        String token=session().get("token").asText();
        create(token,List.of());
        var paused=definition(List.of());paused.put("status","PAUSED");
        send(post("/api/goals"),token,request(paused,0)).andExpect(status().isOk());
        var archived=definition(List.of());archived.put("archived",true);
        send(post("/api/goals"),token,request(archived,0)).andExpect(status().isOk());
        send(get("/api/goals").param("statuses","ACTIVE","PAUSED").param("archiveScope","ALL").param("size","1"),token,null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.content.length()").value(1));
        send(get("/api/goals").param("status","PAUSED"),token,null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/goals").param("archived","true"),token,null).andExpect(jsonPath("$.totalElements").value(1));
        send(get("/api/goals").param("statuses","ENDED"),token,null).andExpect(jsonPath("$.totalElements").value(0));
        send(get("/api/goals").param("statuses",""),token,null).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
        send(get("/api/goals").param("statuses","UNKNOWN"),token,null).andExpect(status().isBadRequest());
        send(get("/api/goals").param("sort","untrusted"),token,null).andExpect(status().isBadRequest());
        send(get("/api/goals").param("archiveScope","ALL"),session().get("token").asText(),null).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test void trackingPreviewDoesNotRequireGoalNameAndThreadOptionsHaveContext() throws Exception {
        String token=session().get("token").asText();
        long experiment=json(send(post("/api/experiments"),token,Map.of("title","同名線的所屬實驗")).andExpect(status().isOk())).get("id").asLong();
        long thread=json(send(put("/api/experiments/"+experiment+"/activity/focus"),token,Map.of("newContent","下一步","expectedVersion",0)).andExpect(status().isOk())).get("currentThreadId").asLong();
        send(get("/api/goals/options").param("kind","THREAD").param("experimentId",String.valueOf(experiment)),token,null)
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].parentId").value(experiment)).andExpect(jsonPath("$[0].parentTitle").value("同名線的所屬實驗"));
        var goal=create(token,List.of(source("THREAD",thread)));
        send(get("/api/goals/"+goal.get("id").asLong()+"/sources"),token,null).andExpect(jsonPath("$[0].parentId").value(experiment));
        send(post("/api/goals/tracking-preview"),token,Map.of("sources",List.of(source("THREAD",thread)),"allTags",false)).andExpect(status().isOk()).andExpect(jsonPath("$.cardCount").value(0));
        send(post("/api/goals/tracking-preview"),token,definition(List.of())).andExpect(status().isOk());
        send(post("/api/goals/tracking-preview"),session().get("token").asText(),Map.of("sources",List.of(source("THREAD",thread)))).andExpect(status().isBadRequest());
    }

    @Test void pinOrderNormalizesTiesPreservesRuleRevisionsAndRejectsRepeatOrForeignMoves() throws Exception {
        String token=session().get("token").asText();
        long first=create(token,List.of()).get("id").asLong(),second=create(token,List.of()).get("id").asLong(),third=create(token,List.of()).get("id").asLong();
        var move=Map.of("direction","DOWN","version",1,"adjacentId",second,"adjacentVersion",1);
        send(post("/api/goals/"+first+"/pin-order"),token,move).andExpect(status().isOk());
        var ordered=json(send(get("/api/goals").param("pinned","true").param("sort","pinOrder"),token,null).andExpect(status().isOk())).get("content");
        assertThat(ordered.get(0).get("id").asLong()).isEqualTo(second);
        assertThat(ordered.get(1).get("id").asLong()).isEqualTo(first);
        assertThat(ordered.get(2).get("id").asLong()).isEqualTo(third);
        for(var goal:ordered) {
            var revisions=json(send(get("/api/goals/"+goal.get("id").asLong()+"/revisions"),token,null).andExpect(status().isOk()));
            assertThat(revisions.get(0).get("revision").asLong()).isEqualTo(goal.get("version").asLong());
        }
        send(post("/api/goals/"+first+"/pin-order"),token,move).andExpect(status().isConflict());
        send(post("/api/goals/"+first+"/pin-order"),session().get("token").asText(),move).andExpect(status().isNotFound());
        send(get("/api/goals/overview"),token,null).andExpect(jsonPath("$[0].id").value(second));
        assertThat(board(token,true,List.of(),null,UUID.randomUUID()).get("selectedGoalCount").asInt()).isEqualTo(3);
    }

    @Test void createsStandaloneGoalAndNeverFallsBackToAllCards() throws Exception {
        var session=session();String token=session.get("token").asText();var owner=users.findById(session.get("id").asLong()).orElseThrow();
        cards.saveAndFlush(Card.builder().user(owner).type("note").title("未追蹤收藏").build());
        var goal=create(token,List.of());
        send(get("/api/goals/{id}",goal.get("id").asLong()),token,null).andExpect(jsonPath("$.cardCount").value(0));
        var empty=board(token,false,List.of(),null,UUID.randomUUID());assertThat(empty.get("content").size()).isZero();assertThat(empty.get("emptyReason").asText()).isEqualTo("NO_SELECTION");
        var none=board(token,true,List.of(),null,UUID.randomUUID());assertThat(none.get("total").asLong()).isZero();assertThat(none.get("emptyReason").asText()).isEqualTo("NO_CARD_SOURCES");
        send(get("/api/goals/{id}",goal.get("id").asLong()),session().get("token").asText(),null).andExpect(status().isNotFound());
    }
    @Test void cardAssociationExplainsTheMatchingTrackingSource() throws Exception {
        var session=session();String token=session.get("token").asText();var user=users.findById(session.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("直接追蹤的卡片").build());create(token,List.of(source("CARD",card.getId())));
        send(get("/api/goal-associations").param("kind","CARD").param("id",String.valueOf(card.getId())),token,null).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reasons[0].label").value("直接選入此卡片")).andExpect(jsonPath("$[0].reasons[0].direct").value(false));
    }
    @Test void inactiveGoalDoesNotShowSourceAssociationBadge() throws Exception {
        var session=session();String token=session.get("token").asText();var user=users.findById(session.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("暫停目標的卡片").build());var goal=create(token,List.of(source("CARD",card.getId())));
        var paused=mapper.convertValue(goal.get("definition"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});paused.put("status","PAUSED");
        send(put("/api/goals/{id}",goal.get("id").asLong()),token,Map.of("definition",paused,"version",1,"reason","暫停測試","requestId",UUID.randomUUID())).andExpect(status().isOk());
        send(get("/api/goal-associations").param("kind","CARD").param("id",String.valueOf(card.getId())),token,null).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
        send(post("/api/goal-associations/summary"),token,Map.of("objects",List.of(source("CARD",card.getId())))).andExpect(status().isOk()).andExpect(jsonPath("$['CARD:"+card.getId()+"']").value(0));
    }
    @Test void fiveCardBatchesDeduplicateAndNeverChangeCardScheduleOrUpdatedTime() throws Exception {
        var session=session();String token=session.get("token").asText();var user=users.findById(session.get("id").asLong()).orElseThrow();
        List<Map<String,Object>> sources=new ArrayList<>();List<Long> ids=new ArrayList<>();
        for(int i=0;i<12;i++){var c=cards.saveAndFlush(Card.builder().user(user).type("note").title("材料"+i).intervalDays(i==0?null:30).nextShowAt(ZonedDateTime.now(clock).plusDays(30)).build());ids.add(c.getId());sources.add(source("CARD",c.getId()));}
        create(token,sources);var second=create(token,sources);em.flush();em.clear();
        var before=cards.findById(ids.getFirst()).orElseThrow();var updated=before.getUpdatedAt();var next=before.getNextShowAt();
        UUID seed=UUID.randomUUID();var first=board(token,true,List.of(),null,seed);assertThat(first.get("content").size()).isEqualTo(5);assertThat(first.get("total").asInt()).isEqualTo(12);
        Set<Long> seen=new HashSet<>();first.get("content").forEach(c->seen.add(c.get("id").asLong()));
        var page=board(token,true,List.of(),first.get("nextCursor").asText(),seed);assertThat(page.get("content").size()).isEqualTo(5);
        page.get("content").forEach(c->assertThat(seen.add(c.get("id").asLong())).isTrue());
        var last=board(token,true,List.of(),page.get("nextCursor").asText(),seed);assertThat(last.get("content").size()).isEqualTo(2);assertThat(last.get("hasMore").asBoolean()).isFalse();
        em.clear();var after=cards.findById(ids.getFirst()).orElseThrow();assertThat(after.getUpdatedAt()).isEqualTo(updated);assertThat(after.getNextShowAt()).isEqualTo(next);assertThat(after.getOpenCount()).isZero();
        var definition=mapper.convertValue(second.get("definition"),new tools.jackson.core.type.TypeReference<Map<String,Object>>(){});definition.put("status","PAUSED");
        send(put("/api/goals/{id}",second.get("id").asLong()),token,Map.of("definition",definition,"version",1,"reason","暫停","requestId",UUID.randomUUID())).andExpect(status().isOk());
        var paused=board(token,false,List.of(second.get("id").asLong()),null,UUID.randomUUID());assertThat(paused.get("total").asInt()).isEqualTo(12);
    }
    @Test void sameDayTenTextChangesHaveOneSummaryAndMetadataDoesNotCount() throws Exception {
        var session=session();String token=session.get("token").asText();var user=users.findById(session.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("草稿").content("原文").build());
        create(token,List.of(source("CARD",card.getId())));
        for(int i=0;i<10;i++)send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","草稿","cardNote","筆記"+i,"content","原文","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        em.flush();assertThat(em.createQuery("select count(d) from CardContentDayRow d where d.user.id=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()).isEqualTo(1);
        assertThat(em.createQuery("select count(l) from GoalCardDayRow l where l.user.id=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()).isEqualTo(1);
        var time=cards.findById(card.getId()).orElseThrow().getContentUpdatedAt();assertThat(time).isNotNull();
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","改標題","cardNote","筆記9","content","原文","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        assertThat(cards.findById(card.getId()).orElseThrow().getContentUpdatedAt()).isEqualTo(time);
        create(token,List.of(source("CARD",card.getId())));
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","草稿","cardNote","新增目標後加工","content","原文","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        assertThat(em.createQuery("select count(l) from GoalCardDayRow l where l.user.id=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()).isEqualTo(2);
        var yesterday=em.createQuery("select d from CardContentDayRow d where d.user.id=:owner",CardContentDayRow.class).setParameter("owner",user.getId()).getSingleResult();yesterday.modifiedOn=LocalDate.now(clock).minusDays(1);em.flush();
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","草稿","cardNote","隔日加工","content","原文","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        assertThat(em.createQuery("select count(d) from CardContentDayRow d where d.user.id=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()).isEqualTo(2);
        assertThat(em.createQuery("select count(distinct d.cardId) from CardContentDayRow d where d.user.id=:owner",Long.class).setParameter("owner",user.getId()).getSingleResult()).isEqualTo(1);
    }
    @Test void periodMetricCanExcludeAndRestoreItsContentEvidence() throws Exception {
        var session=session();String token=session.get("token").asText();var user=users.findById(session.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("指標材料").content("原文").build());var goal=create(token,List.of(source("CARD",card.getId())));
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","指標材料","cardNote","有意義修改","content","原文","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        var metrics=json(send(get("/api/goals/{id}/metrics",goal.get("id").asLong()),token,null).andExpect(status().isOk()));assertThat(metrics.get(1).get("metricKey").asText()).isEqualTo("M03");assertThat(metrics.get(1).get("value").asInt()).isEqualTo(1);
        var evidence=json(send(get("/api/goals/{id}/evidence",goal.get("id").asLong()).param("metric","M03"),token,null).andExpect(status().isOk()));String id=evidence.get("content").get(0).get("id").asText();
        send(put("/api/goals/{id}/evidence/{linkId}",goal.get("id").asLong(),id),token,Map.of("excluded",true,"reason","與本期無關","version",0)).andExpect(status().isOk()).andExpect(jsonPath("$.excluded").value(true));
        metrics=json(send(get("/api/goals/{id}/metrics",goal.get("id").asLong()),token,null).andExpect(status().isOk()));assertThat(metrics.get(1).get("value").asInt()).isZero();
        evidence=json(send(get("/api/goals/{id}/evidence",goal.get("id").asLong()).param("metric","M03"),token,null).andExpect(status().isOk()));assertThat(evidence.get("content").get(0).get("excluded").asBoolean()).isTrue();
    }
    @Test void ownerValidationPreferenceConflictsAndGoalDeletionPreserveSources() throws Exception {
        var a=session();String token=a.get("token").asText();var user=users.findById(a.get("id").asLong()).orElseThrow();
        var c=cards.saveAndFlush(Card.builder().user(user).type("note").title("保留").build());var goal=create(token,List.of(source("CARD",c.getId())));
        var other=session().get("token").asText();send(post("/api/goals"),other,request(definition(List.of(source("CARD",c.getId()))),0)).andExpect(status().isBadRequest());
        var preference=Map.of("allActive",false,"goalIds",List.of(goal.get("id").asLong()),"includePaused",true,"includeArchived",false,"version",0);
        send(put("/api/goal-board/preferences"),token,preference).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        send(put("/api/goal-board/preferences"),token,preference).andExpect(status().isConflict());
        send(delete("/api/goals/{id}",goal.get("id").asLong()).param("version","1"),token,null).andExpect(status().isOk());assertThat(cards.existsById(c.getId())).isTrue();
    }
    @Test void progressLogsReviewsAndReminderSnoozeAreOwnedAndVersioned() throws Exception {
        var auth=session();String token=auth.get("token").asText();var definition=definition(List.of());definition.put("endsOn",LocalDate.now(clock).toString());long id=json(send(post("/api/goals"),token,request(definition,0)).andExpect(status().isOk())).get("id").asLong();
        var log=json(send(post("/api/goals/{id}/logs",id),token,Map.of("kind","MEASUREMENT","occurredOn",LocalDate.now(clock),"amount",3,"unit","次","version",0)).andExpect(status().isOk()));
        send(get("/api/goals/{id}/logs",id),token,null).andExpect(jsonPath("$[0].amount").value(3));
        var review=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        long reviewId=review.get("id").asLong();
        Map<String,Object> content=Map.of("observation","已完成站外行動","decision","持續一週","direction","ALIGNED","informationHelpfulness",4,"actionEffectiveness",5);
        var saved=json(send(put("/api/goals/{id}/reviews/{reviewId}",id,reviewId),token,Map.of("content",content,"version",1)).andExpect(status().isOk()));
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,reviewId),token,Map.of("version",saved.get("version").asLong(),"requestId",UUID.randomUUID(),"goalVersion",1)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isBadRequest());
        send(post("/api/goal-reminders/snooze"),token,Map.of("goalId",id,"until",ZonedDateTime.now(clock).plusHours(1),"version",0)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        send(delete("/api/goals/{id}/logs/{logId}",id,log.get("id").asLong()).param("version",log.get("version").asText()),token,null).andExpect(status().isOk());
    }
    @Test void reviewResumesOlderDraftAndFreezesOnlyItsPeriod() throws Exception {
        var auth=session();String token=auth.get("token").asText();var user=users.findById(auth.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("復盤期間材料").content("原文").build());
        long id=create(token,List.of(source("CARD",card.getId()))).get("id").asLong();
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","復盤期間材料","content","今天修改","cardNote","備註","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        var today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Taipei")));
        send(post("/api/goals/{id}/logs",id),token,Map.of("kind","NOTE","occurredOn",today.minusDays(1),"content","昨天補記","version",0)).andExpect(status().isOk());
        send(post("/api/goals/{id}/logs",id),token,Map.of("kind","NOTE","occurredOn",today,"content","今天補記","version",0)).andExpect(status().isOk());
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        // 模擬前一日建立的草稿，次日仍應取得同一筆，而非產生重疊期間。
        em.find(GoalReviewRow.class,first.get("id").asLong()).endsOn=today.minusDays(1);em.flush();
        var resumed=json(send(get("/api/goals/{id}/reviews/{reviewId}",id,first.get("id").asLong()),token,null).andExpect(status().isOk()));
        assertThat(resumed.get("id")).isEqualTo(first.get("id"));
        assertThat(resumed.at("/snapshot/logs").size()).isEqualTo(1);
        assertThat(resumed.at("/snapshot/metrics/1/value").asInt()).isZero();
        var saved=saveReview(token,id,resumed,"ALIGNED");
        var submitted=json(send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,saved.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),null)).andExpect(status().isOk()));
        var d=definition(List.of());d.put("name","後來更名");
        send(put("/api/goals/{id}",id),token,request(d,1)).andExpect(status().isOk());
        send(post("/api/goals/{id}/logs",id),token,Map.of("kind","NOTE","occurredOn",today.minusDays(1),"content","事後新增補記","version",0)).andExpect(status().isOk());
        var later=json(send(get("/api/goals/{id}/reviews/{reviewId}",id,saved.get("id").asLong()),token,null).andExpect(status().isOk()));
        assertThat(later.get("snapshot")).isEqualTo(submitted.get("snapshot"));
        var next=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        assertThat(next.get("startsOn").asText()).isEqualTo(today.toString());
    }
    @Test void reviewModificationDaysCountDistinctDatesRatherThanCardDays() throws Exception {
        var auth=session();String token=auth.get("token").asText();var user=users.findById(auth.get("id").asLong()).orElseThrow();
        var first=cards.saveAndFlush(Card.builder().user(user).type("note").title("第一張").content("原文").build());
        var second=cards.saveAndFlush(Card.builder().user(user).type("note").title("第二張").content("原文").build());
        var d=definition(List.of(source("CARD",first.getId()),source("CARD",second.getId())));d.put("metrics",List.of("M03","M04"));
        long id=json(send(post("/api/goals"),token,request(d,0)).andExpect(status().isOk())).get("id").asLong();
        for(var card:List.of(first,second))send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title",card.getTitle(),"content","修改內容","cardNote","備註","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()).andExpect(jsonPath("$.snapshot.metrics[0].value").value(2)).andExpect(jsonPath("$.snapshot.metrics[1].value").value(1)).andExpect(jsonPath("$.snapshot.metrics[1].unit").value("天"));
    }
    @Test void reviewStatusChangesAreAtomicIdempotentAndEditsDoNotReplayThem() throws Exception {
        String token=session().get("token").asText();long id=create(token,List.of()).get("id").asLong();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        var saved=saveReview(token,id,first,"COMPLETE");var submission=reviewSubmission(saved,UUID.randomUUID(),"COMPLETED");
        var submitted=json(send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,submission).andExpect(status().isOk()));
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,submission).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(submitted.get("version").asInt()));
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("COMPLETED")).andExpect(jsonPath("$.version").value(2));
        var changed=saveReview(token,id,submitted,"STOP");
        assertThat(changed.get("status").asText()).isEqualTo("SUBMITTED");
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("COMPLETED")).andExpect(jsonPath("$.version").value(2));
        var result=json(send(get("/api/goals/{id}/reviews/{reviewId}",id,first.get("id").asLong()),token,null).andExpect(status().isOk()));
        assertThat(result.get("snapshot")).isEqualTo(submitted.get("snapshot"));
        String other=session().get("token").asText();
        send(get("/api/goals/{id}/reviews/{reviewId}",id,first.get("id").asLong()),other,null).andExpect(status().isNotFound());
        send(post("/api/goals/{id}/reviews/{reviewId}/revisions",id,first.get("id").asLong()),other,null).andExpect(status().isNotFound());
        send(delete("/api/goals/{id}",id).param("version","2"),token,null).andExpect(status().isOk());
    }
    @Test void changedGoalRejectsReviewSubmissionWithoutPartialStateChange() throws Exception {
        String token=session().get("token").asText();long id=create(token,List.of()).get("id").asLong();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        var saved=saveReview(token,id,first,"STOP");var d=definition(List.of());d.put("name","另一裝置更新");
        send(put("/api/goals/{id}",id),token,request(d,1)).andExpect(status().isOk());
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),"ENDED")).andExpect(status().isConflict());
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("ACTIVE"));
        send(get("/api/goals/{id}/reviews/{reviewId}",id,first.get("id").asLong()),token,null).andExpect(jsonPath("$.status").value("DRAFT"));
    }
    @Test void reviewPreviewDoesNotWriteAndCompletedReviewEditsInPlace() throws Exception {
        String token=session().get("token").asText();long id=create(token,List.of()).get("id").asLong();
        send(get("/api/goals/{id}/reviews/preview",id),token,null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        send(get("/api/goals/{id}/reviews",id),token,null).andExpect(jsonPath("$.length()").value(0));
        send(post("/api/goals/{id}/reviews",id),token,null).andExpect(status().isMethodNotAllowed());
        send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation"," ")).andExpect(status().isBadRequest());
        var initial=Map.of("observation","初次觀察","direction","ALIGNED");
        var draft=json(send(post("/api/goals/{id}/reviews/initialize",id),token,initial).andExpect(status().isOk()));
        send(post("/api/goals/{id}/reviews/initialize",id),token,initial).andExpect(status().isConflict());
        var submitted=json(send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,draft.get("id").asLong()),token,reviewSubmission(draft,UUID.randomUUID(),null)).andExpect(status().isOk()));
        var updated=Map.of("observation","補充觀察","direction","STOP");
        var result=json(send(put("/api/goals/{id}/reviews/{reviewId}",id,draft.get("id").asLong()),token,Map.of("version",submitted.get("version").asLong(),"content",updated)).andExpect(status().isOk()));
        assertThat(result.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(result.get("snapshot")).isEqualTo(submitted.get("snapshot"));
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("ACTIVE"));
        send(get("/api/goals/{id}/reviews",id),token,null).andExpect(jsonPath("$.length()").value(1));
        send(put("/api/goals/{id}/reviews/{reviewId}",id,draft.get("id").asLong()),token,Map.of("version",submitted.get("version").asLong(),"content",updated)).andExpect(status().isConflict());
    }
    @Test void databaseRejectsDuplicateReviewPeriod() throws Exception {
        String token=session().get("token").asText();long id=create(token,List.of()).get("id").asLong();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(first,UUID.randomUUID(),null)).andExpect(status().isOk());
        var original=em.find(GoalReviewRow.class,first.get("id").asLong());var duplicate=new GoalReviewRow();
        duplicate.user=original.user;duplicate.goal=original.goal;duplicate.startsOn=original.startsOn;duplicate.endsOn=original.endsOn;
        duplicate.status="SUBMITTED";duplicate.content=original.content;duplicate.snapshot=original.snapshot;duplicate.version=1;
        duplicate.createdAt=ZonedDateTime.now(clock);duplicate.updatedAt=duplicate.createdAt;
        assertThatThrownBy(()->{em.persist(duplicate);em.flush();}).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("uq_goal_review_period");
    }
    @Test void finalPeriodReviewStopsReminderEvenWhenGoalRemainsActive() throws Exception {
        String token=session().get("token").asText();var d=definition(List.of());d.put("endsOn",LocalDate.now(clock).toString());
        long id=json(send(post("/api/goals"),token,request(d,0)).andExpect(status().isOk())).get("id").asLong();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));var saved=saveReview(token,id,first,"ALIGNED");
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),null)).andExpect(status().isOk());
        send(get("/api/goal-reminders"),token,null).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        assertThat(goals.reminder(em.find(GoalRow.class,id),ZonedDateTime.now(clock).plusDays(30).withHour(10))).isEmpty();
    }
    @Test void reviewRequiresMatchingStateAndKeepsLegacySnapshotsReadable() throws Exception {
        String token=session().get("token").asText();long id=create(token,List.of()).get("id").asLong();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));var saved=saveReview(token,id,first,"PAUSE");
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),null)).andExpect(status().isBadRequest());
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("ACTIVE"));
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),"PAUSED")).andExpect(status().isOk());
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("PAUSED"));
        var row=em.find(GoalReviewRow.class,first.get("id").asLong());
        row.snapshot=mapper.writeValueAsString(Map.of("definition",definition(List.of()),"metrics",List.of(),"coverageAt",ZonedDateTime.now(clock)));em.flush();
        send(get("/api/goals/{id}/reviews/{reviewId}",id,row.id),token,null).andExpect(status().isOk()).andExpect(jsonPath("$.snapshot.definition.name").value("寫作方向"));
    }
    @Test void completingReviewPreservesDeletedSourceAndOwnerCleanupStillWorks() throws Exception {
        var auth=session();String token=auth.get("token").asText();var user=users.findById(auth.get("id").asLong()).orElseThrow();
        var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("已刪除來源").build());long id=create(token,List.of(source("CARD",card.getId()))).get("id").asLong();
        em.flush();em.clear();
        send(delete("/api/cards/{id}",card.getId()),token,null).andExpect(status().isOk());em.flush();em.clear();
        var first=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));var saved=saveReview(token,id,first,"STOP");
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,first.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),"ENDED")).andExpect(status().isOk());
        send(get("/api/goals/{id}",id),token,null).andExpect(jsonPath("$.definition.status").value("ENDED"));
        goals.clearOwner(user.getId());assertEmpty(user.getId());
    }
    JsonNode saveReview(String token,long id,JsonNode review,String direction)throws Exception {
        return json(send(put("/api/goals/{id}/reviews/{reviewId}",id,review.get("id").asLong()),token,Map.of("version",review.get("version").asLong(),"content",Map.of("observation","觀察","decision","下一步","direction",direction))).andExpect(status().isOk()));
    }
    Map<String,Object> reviewSubmission(JsonNode review,UUID key,String target) {
        Map<String,Object> request=new LinkedHashMap<>();request.put("version",review.get("version").asLong());request.put("requestId",key);request.put("goalVersion",review.get("goalVersion").asLong());if(target!=null)request.put("targetStatus",target);return request;
    }
    @Test void demoResetAndExpiryCleanAllGoalTables() throws Exception {
        var session=json(send(post("/api/auth/demo-sessions"),null,Map.of("library","writing")).andExpect(status().isOk()));String token=session.get("token").asText();long owner=session.get("id").asLong();
        var user=users.findById(owner).orElseThrow();var card=cards.saveAndFlush(Card.builder().user(user).type("note").title("目標測試資料").build());var tracked=create(token,List.of(source("CARD",card.getId())));long id=tracked.get("id").asLong();
        send(put("/api/cards/{id}",card.getId()),token,Map.of("type","note","title","目標測試資料","cardNote","加工","expectedContentVersion",0,"isArchived",false,"tags",List.of())).andExpect(status().isOk());
        send(get("/api/goals"),token,null).andExpect(jsonPath("$.totalElements").value(1));
        send(post("/api/goals/{id}/logs",id),token,Map.of("kind","NOTE","occurredOn",LocalDate.now(clock),"content","展示補記","version",0)).andExpect(status().isOk());
        var draft=json(send(post("/api/goals/{id}/reviews/initialize",id),token,Map.of("observation","initial","direction","ALIGNED")).andExpect(status().isOk()));var saved=saveReview(token,id,draft,"ALIGNED");
        send(post("/api/goals/{id}/reviews/{reviewId}/submit",id,draft.get("id").asLong()),token,reviewSubmission(saved,UUID.randomUUID(),null)).andExpect(status().isOk());
        send(post("/api/goal-reminders/snooze"),token,Map.of("goalId",id,"until",ZonedDateTime.now(clock).plusHours(1),"version",0)).andExpect(status().isOk());
        send(post("/api/auth/demo-sessions/reset"),token,null).andExpect(status().isOk());em.flush();em.clear();assertEmpty(owner);
        create(token,List.of());users.findById(owner).orElseThrow().setDemoExpiresAt(ZonedDateTime.now(clock).minusMinutes(1));em.flush();demo.clearExpiredSessions();em.flush();em.clear();assertEmpty(owner);assertThat(users.existsById(owner)).isFalse();
    }
    @Test void discoveryAndOrganizationFactsSurviveClearingAndStayScopedToSelectedThreads() throws Exception {
        var auth=session();String token=auth.get("token").asText();long owner=auth.get("id").asLong();
        long experiment=json(send(post("/api/experiments"),token,Map.of("title","隔離目標實驗")).andExpect(status().isOk())).get("id").asLong();
        String activity="/api/experiments/"+experiment+"/activity";
        var focus=json(send(put(activity+"/focus"),token,Map.of("newContent","試寫衝突","expectedVersion",0)).andExpect(status().isOk()));
        long thread=focus.get("currentThreadId").asLong();create(token,List.of(source("THREAD",thread)));
        send(post(activity+"/records"),token,Map.of("threadId",thread,"discovery","發現人物動機")).andExpect(status().isOk());
        var preview=json(send(post(activity+"/organizations/preview"),token,Map.of("mode","HISTORY","groups",List.of(Map.of("threadId",thread,"allRecords",true)))).andExpect(status().isOk()));
        var group=preview.get("groups").get(0);Map<String,Object> body=Map.of("token",preview.get("token").asText(),"requestId",UUID.randomUUID().toString(),"clearRecords",true,"drafts",List.of(Map.of("key",group.get("key").asText(),"text","整理所得")));
        send(post(activity+"/organizations"),token,body).andExpect(status().isOk());send(post(activity+"/organizations"),token,body).andExpect(status().isOk());em.flush();
        var facts=em.createQuery("select f from GoalFactRow f where f.user.id=:owner",GoalFactRow.class).setParameter("owner",owner).getResultList();
        assertThat(facts.stream().filter(f->f.type.equals("DISCOVERY_CREATED"))).hasSize(1);
        assertThat(facts.stream().filter(f->f.type.equals("EXPLORATION_ORGANIZED"))).hasSize(1);
        var link=em.createQuery("select l from GoalFactLinkRow l where l.user.id=:owner and l.fact.type='EXPLORATION_ORGANIZED'",GoalFactLinkRow.class).setParameter("owner",owner).getSingleResult();
        assertThat(mapper.readTree(link.details).get("clearedCount").asInt()).isEqualTo(1);
        assertThat(((Number)em.createNativeQuery("select count(*) from experiment_exploration_records where experiment_id=:id").setParameter("id",experiment).getSingleResult()).longValue()).isZero();
    }
    @Test void oneAcquisitionCanSupportExperimentAndWorkGoalsWithoutDuplicatingTheFact() throws Exception {
        var auth=session();String token=auth.get("token").asText();long owner=auth.get("id").asLong();
        long experiment=json(send(post("/api/experiments"),token,Map.of("title","成果來源")).andExpect(status().isOk())).get("id").asLong();
        long work=json(send(post("/api/creative-works"),token,Map.of("title","短篇","editorMode","INTERNAL","experimentId",experiment)).andExpect(status().isOk())).get("work").get("id").asLong();
        create(token,List.of(source("EXPERIMENT",experiment)));create(token,List.of(source("WORK",work)));
        send(put("/api/creative-works/{id}/stage",work),token,Map.of("stage","SHAREABLE")).andExpect(status().isOk());
        send(post("/api/creative-works/{id}/treasure",work),token,Map.of("title","入藏短篇","acquiredOn",LocalDate.now(clock),"sourceExperimentId",experiment)).andExpect(status().isOk());em.flush();
        assertThat(em.createQuery("select count(f) from GoalFactRow f where f.user.id=:owner and f.type='TREASURE_ACQUIRED'",Long.class).setParameter("owner",owner).getSingleResult()).isEqualTo(1);
        assertThat(em.createQuery("select count(l) from GoalFactLinkRow l where l.user.id=:owner and l.fact.type='TREASURE_ACQUIRED'",Long.class).setParameter("owner",owner).getSingleResult()).isEqualTo(2);
    }
    void assertEmpty(long owner){for(String table:List.of("GoalRow","GoalRuleRow","GoalSourceRow","GoalFactRow","GoalFactLinkRow","CardContentDayRow","GoalCardDayRow","GoalPreferenceRow","GoalEvidenceOverrideRow","GoalProgressLogRow","GoalReviewRow","GoalReminderRow"))assertThat(em.createQuery("select count(r) from "+table+" r where r.user.id=:owner",Long.class).setParameter("owner",owner).getSingleResult()).as(table).isZero();}
    Map<String,Object> definition(List<Map<String,Object>> sources){var today=LocalDate.now(clock);Map<String,Object> result=new LinkedHashMap<>();result.put("name","寫作方向");result.put("startsOn",today.minusDays(1));result.put("endsOn",today.plusDays(30));result.put("status","ACTIVE");result.put("reviewDays",7);result.put("pinned",true);result.put("archived",false);result.put("pinOrder",0);result.put("remindersEnabled",true);result.put("allTags",false);result.put("sources",sources);result.put("metrics",List.of("M01","M03"));return result;}
    Map<String,Object> request(Map<String,Object> definition,long version){return Map.of("definition",definition,"version",version,"reason","測試","requestId",UUID.randomUUID());}
    Map<String,Object> source(String kind,long id){return Map.of("kind",kind,"id",id,"excluded",false);}
    JsonNode create(String token,List<Map<String,Object>> sources)throws Exception{return json(send(post("/api/goals"),token,request(definition(sources),0)).andExpect(status().isOk()));}
    JsonNode board(String token,boolean all,List<Long> ids,String cursor,UUID seed)throws Exception{Map<String,Object> body=new LinkedHashMap<>();body.put("selection",Map.of("allActive",all,"goalIds",ids,"includePaused",true,"includeArchived",false,"version",0));body.put("seed",seed);body.put("cursor",cursor);return json(send(post("/api/goal-board/cards"),token,body).andExpect(status().isOk()));}
    JsonNode session()throws Exception{String key=UUID.randomUUID().toString();return json(send(post("/api/auth/register"),null,Map.of("username","goal-"+key.substring(0,8),"email",key+"@example.com","password","password123")).andExpect(status().isOk()));}
    ResultActions send(MockHttpServletRequestBuilder request,String token,Object body)throws Exception{if(token!=null)request.header("Authorization","Bearer "+token);if(body!=null)request.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));return mvc.perform(request);}
    JsonNode json(ResultActions action)throws Exception{return mapper.readTree(action.andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
}
