package com.echogallery.controller.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class GlobalExceptionHandlerTests {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void uniqueViolationIsReportedAsConflict() {
        var response = handler.handleConflict(new DataIntegrityViolationException("write failed", new SQLException("private detail", "23505")));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("資料已存在");
    }

    @Test
    void foreignKeyViolationAsksToReselectRelation() {
        var response = handler.handleConflict(new DataIntegrityViolationException("write failed", new RuntimeException(new SQLException("private detail", "23503"))));
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody().message()).contains("關聯資料");
    }

    @Test
    void missingRequiredColumnDoesNotAskUserToChangeTheirData() {
        var response = handler.handleConflict(new DataIntegrityViolationException("write failed", new SQLException("kind must not be null", "23502")));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).contains("無法儲存").doesNotContain("已存在", "kind");
    }

    @Test
    void unclassifiedViolationIsNotGuessedFromMessage() {
        var response = handler.handleConflict(new DataIntegrityViolationException("duplicate or other write error"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).doesNotContain("已存在", "duplicate");
    }
}
