package com.echogallery.exception;

/**
 * 統一 API 錯誤回應載體 (DTO)。
 *
 * 當系統發生異常並被 {@code GlobalExceptionHandler} 攔截時，
 * 會將錯誤資訊封裝為此物件並轉換為 JSON 回傳給前端。
 *
 * @param message 友好的錯誤提示訊息，用於前端直接顯示給使用者或記錄 Log
 * @param status  HTTP 狀態碼 (例如 400, 401, 500) 或內部定義的商業錯誤代碼
 */
public record ErrorResponse (String message, int status) {

}
