package com.echogallery.experiment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 保留舊探索網址的明確退場回應，避免舊版前端把 404 誤判為暫時性資料問題。
 */
@RestController
public class RetiredExplorationController {

    @RequestMapping({
            "/api/experiments/{experimentId}/exploration", "/api/experiments/{experimentId}/exploration/**",
            "/api/experiments/{experimentId}/activity", "/api/experiments/{experimentId}/activity/**",
            "/api/experiments/{experimentId}/threads", "/api/experiments/{experimentId}/threads/**",
            "/api/cards/{cardId}/exploration-threads", "/api/cards/{cardId}/exploration-threads/**",
            "/api/creative-works/{workId}/exploration-threads", "/api/creative-works/{workId}/exploration-threads/**"
    })
    public void retired() {
        throw new ResponseStatusException(HttpStatus.GONE, "探索線功能已退場，請改用探索紀錄");
    }
}
