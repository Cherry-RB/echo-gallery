package com.echogallery.work;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import com.echogallery.support.IntegrationTestBase;
import com.echogallery.treasure.TreasureLifecycle;
import com.echogallery.user.UserRepository;
import tools.jackson.databind.*;

@AutoConfigureMockMvc
class WorkConcurrencyIntegrationTests extends IntegrationTestBase {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired CreativeWorkRepository works;
    @Autowired UserRepository users;
    @Autowired TreasureLifecycle lifecycle;
    @Autowired TransactionTemplate transactions;
    private final List<Long> owners = new ArrayList<>();
    @AfterEach void cleanup() {
        transactions.executeWithoutResult(status -> {
            for (Long owner : owners) { lifecycle.clearOwner(owner); users.deleteById(owner); }
        });
    }
    @Test void onlyOneOfTwoConcurrentSavesWithSameVersionCanSucceed() throws Exception {
        JsonNode user = register(); String token = user.get("token").asText();
        long id = create(token, Map.of("title", "同時續寫", "editorMode", "INTERNAL"));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (String body : List.of("第一個分頁", "第二個分頁")) results.add(executor.submit(() -> {
                start.await();
                return mvc.perform(put("/api/creative-works/{id}/body", id).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("body", body, "expectedVersion", 0))))
                        .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
        var saved = works.findById(id).orElseThrow();
        assertThat(saved.getBodyVersion()).isEqualTo(1);
        assertThat(saved.getBody()).isIn("第一個分頁", "第二個分頁");
    }
    @Test void failedSourceCreationRollsBackWholeWorkInsteadOfLeavingDuplicates() throws Exception {
        JsonNode user = register(); String token = user.get("token").asText(); long owner = user.get("id").asLong();
        mvc.perform(post("/api/creative-works").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("title", "來源失效", "editorMode", "INTERNAL", "sources", List.of(Map.of("recordId", Long.MAX_VALUE))))))
                .andExpect(status().isBadRequest());
        assertThat(works.findAll().stream().filter(w -> w.getUser().getId().equals(owner))).isEmpty();
    }
    private JsonNode register() throws Exception {
        String key = UUID.randomUUID().toString();
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("username", "work-" + key.substring(0, 8), "email", key + "@example.com", "password", "password123"))))
                .andExpect(status().isOk()).andReturn();
        var user = mapper.readTree(result.getResponse().getContentAsString()); owners.add(user.get("id").asLong()); return user;
    }
    private long create(String token, Object body) throws Exception {
        var result = mvc.perform(post("/api/creative-works").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).get("work").get("id").asLong();
    }
}
