package com.echogallery.controller.advice;

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

    // 3. 處理資料庫衝突 (例如 Username 重複)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
        // 這裡可以精細一點判斷是哪個欄位，或者簡單回傳
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("此資料已存在，請更換後重試", 409));
    }

}
