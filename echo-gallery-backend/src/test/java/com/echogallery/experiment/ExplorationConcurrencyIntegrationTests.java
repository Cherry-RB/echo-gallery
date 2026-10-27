package com.echogallery.experiment;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.user.UserRepository;
import com.echogallery.card.CardRepository;
import com.echogallery.treasure.TreasureLifecycle;
import tools.jackson.databind.*;

@AutoConfigureMockMvc
class ExplorationConcurrencyIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired ExperimentRepository experiments;
    @Autowired CardRepository cards;
    @Autowired TreasureLifecycle lifecycle;
    @Autowired TransactionTemplate transactions;
    private final List<Long> owners = new ArrayList<>();
    record Context(String token, String root) {}
    @AfterEach void cleanup() {
        transactions.executeWithoutResult(tx -> owners.forEach(owner -> {
            lifecycle.clearOwner(owner);
            experiments.deleteAll(experiments.findAllByUserId(owner)); experiments.flush();
            cards.deleteAll(cards.findAllByUserId(owner)); cards.flush();
            users.deleteById(owner);
        }));
    }
    Context context() throws Exception {
        String key = UUID.randomUUID().toString();
        var user = json(post("/api/auth/register"), null, Map.of("username", key, "email", key+"@example.com", "password", "password123"));
        owners.add(user.get("id").asLong());
        String token = user.get("token").asText();
        long id = json(post("/api/experiments"), token, Map.of("title", "併發隔離驗證")).get("id").asLong();
        return new Context(token, "/api/experiments/" + id + "/activity");
    }
    ResultActions send(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(req);
    }
    JsonNode json(MockHttpServletRequestBuilder req, String token, Object body) throws Exception {
        return mapper.readTree(send(req, token, body).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    List<Integer> concurrent(Context c, String path, Object first, Object second) throws Exception {
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (var payload : List.of(first, second)) results.add(pool.submit(() -> {
                gate.await();
                return send(post(path), c.token(), payload).andReturn().getResponse().getStatus();
            }));
            gate.countDown();
            return List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS));
        }
    }
    @Test void concurrentSameNameCreatesOnlyOneMethodAndOneDefaultThread() throws Exception {
        var c = context();
        var body = Map.of("content", "同一試法");
        assertThat(concurrent(c, c.root()+"/methods", body, body)).containsExactlyInAnyOrder(200,409);
        var state = json(get(c.root()), c.token(), null);
        assertThat(state.get("recordCount").asLong()).isZero();
        assertThat(json(get(c.root()+"/methods"), c.token(), null).get("totalElements").asLong()).isEqualTo(1);
        assertThat(json(get(c.root().replace("/activity","/threads/summaries")), c.token(), null).get("totalElements").asLong()).isEqualTo(1);
    }
    @Test void concurrentRetriesAppendOnlyOnceAndPreserveUnselectedData() throws Exception {
        var c = context();
        var method = json(post(c.root()+"/methods"), c.token(), Map.of("content","原試法"));
        long id = method.get("id").asLong(), thread = method.get("threadId").asLong();
        var record = json(post(c.root()+"/records"), c.token(), Map.of("threadId",thread,"methodId",id,"discovery","原始觀察","expectedMethodVersion",0));
        var preview = json(post(c.root()+"/organizations/preview"), c.token(), Map.of("mode","HISTORY","groups",List.of(Map.of("threadId",thread,"methodId",id,"recordIds",List.of(record.get("id").asLong()),"includeMethod",true))));
        var body = Map.of("token",preview.get("token").asText(),"requestId",UUID.randomUUID().toString(),
            "clearRecords",true,"drafts",List.of(Map.of("key","method:"+id,"text","這輪整理")));
        assertThat(concurrent(c,c.root()+"/organizations",body,body)).containsExactly(200,200);
        var history = json(get(c.root()+"/history").param("threadId",""+thread).param("methodId",""+id),c.token(),null).get("text").asText();
        assertThat(history.indexOf("這輪整理")).isEqualTo(history.lastIndexOf("這輪整理"));
        assertThat(json(get(c.root()),c.token(),null).get("recordCount").asLong()).isZero();
        // 同一預覽改用其他請求識別，不能繞過已變動的資料版本。
        var changed = new HashMap<String,Object>(body); changed.put("requestId",UUID.randomUUID().toString());
        send(post(c.root()+"/organizations"),c.token(),changed).andExpect(status().isConflict());
    }
    @Test void findingFromAnOutdatedMethodSelectionCannotBeAttachedAfterRename() throws Exception {
        var c = context();
        var method = json(post(c.root()+"/methods"), c.token(), Map.of("content","原試法"));
        long id = method.get("id").asLong(), thread = method.get("threadId").asLong();
        json(put(c.root()+"/methods/"+id), c.token(), Map.of("content","已更新試法","expectedVersion",0));
        send(post(c.root()+"/records"),c.token(),Map.of("threadId",thread,"methodId",id,"discovery","舊視窗觀察","expectedMethodVersion",0)).andExpect(status().isConflict());
        assertThat(json(get(c.root()),c.token(),null).get("recordCount").asLong()).isZero();
    }

    @Test void lateNonContentCardWriteDoesNotOverwriteAnOrganizedAppend() throws Exception {
        var c=context();
        long card=json(post("/api/cards"),c.token(),Map.of("type","note","title","正文保護","content","原文")).get("id").asLong();
        var method=json(post(c.root()+"/methods"),c.token(),Map.of("content","輸出試法"));
        var preview=json(post(c.root()+"/organizations/preview"),c.token(),Map.of("mode","CARD","targetCardId",card,"groups",List.of(Map.of("threadId",method.get("threadId").asLong(),"methodId",method.get("id").asLong(),"recordIds",List.of(),"includeMethod",true))));
        var loaded=new CountDownLatch(1); var appended=new CountDownLatch(1);
        try(var pool=Executors.newSingleThreadExecutor()) {
            var lateWriter=pool.submit(() -> transactions.executeWithoutResult(tx -> {
                var stale=cards.findById(card).orElseThrow();
                loaded.countDown();
                try { if(!appended.await(20,TimeUnit.SECONDS)) throw new IllegalStateException("等待追加逾時"); }
                catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                stale.setOpenCount(stale.getOpenCount()+1);
            }));
            try {
                assertThat(loaded.await(20,TimeUnit.SECONDS)).isTrue();
                json(post(c.root()+"/organizations"),c.token(),Map.of("token",preview.get("token").asText(),"requestId",UUID.randomUUID().toString(),"drafts",List.of(Map.of("key","method:"+method.get("id").asLong(),"text","本輪")),"cardContent","追加內容"));
            } finally { appended.countDown(); }
            lateWriter.get(20,TimeUnit.SECONDS);
        }
        var saved=cards.findById(card).orElseThrow();
        assertThat(saved.getContent()).isEqualTo("原文\n\n---\n\n追加內容");
        assertThat(saved.getContentVersion()).isEqualTo(1);
        assertThat(saved.getOpenCount()).isEqualTo(1);
    }
}
