package com.echogallery.controller.advice;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.echogallery.exception.ErrorResponse;
import com.echogallery.exception.UserAlreadyExistsException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 1. 處理格式驗證錯誤 (例如 Email 格式不對)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return ResponseEntity.badRequest().body(new ErrorResponse(msg, 400));
    }

    // 2. 處理自定義業務邏輯例外 (業務層面的檢查)
    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserExists(UserAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(ex.getMessage(), 409));
    }

    // 只依資料庫錯誤碼判定重複，避免將 schema 或必填約束問題誤導為使用者資料重複。
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        String sqlState = null;
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                sqlState = sql.getSQLState();
                break;
            }
        }
        if ("23505".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("此資料已存在，請更換後重試", 409));
        }
        if ("23503".equals(sqlState)) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("關聯資料已變更或不存在，請重新選擇後重試", 409));
        }
        // 不將 SQL、欄位值或資料庫結構暴露給使用者，也不將整筆資料寫入診斷日誌。
        log.error("資料完整性約束失敗，SQLState={}，例外型別={}", sqlState, ex.getMostSpecificCause().getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("資料暫時無法儲存，請稍後再試", 500));
    }

}
