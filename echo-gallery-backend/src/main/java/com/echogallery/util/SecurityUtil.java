package com.echogallery.util;

import org.springframework.security.core.context.SecurityContextHolder;

import com.echogallery.security.CustomUserDetails;

/**
 * 模擬安全上下文。等未來整合 Spring Security + JWT 後，
 * 只需要改寫這個方法內部的實作即可，Service 層完全不用動！
 */
public class SecurityUtil {
    public static Long getCurrentUserId() {
        // 目前先寫死回傳 1L（假設資料庫中第一個使用者的 ID 是 1）
        // 等加入 Spring Security 後，會改為：
        // SecurityContextHolder.getContext().getAuthentication().getPrincipal()...
        // return 1L;
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            return null;
        }
        try {
            Object principal =  SecurityContextHolder
                    .getContext()
                    .getAuthentication()
                    .getPrincipal();
                    // 運用 instanceof 進行防禦，優雅、安全且高效
            if (principal instanceof CustomUserDetails) {
                return ((CustomUserDetails) principal).getId();
            }
            return null;
        } catch (Exception e) {
            // 若未登入或解析出錯，回傳 null（亦可拋出對應的自訂異常）
            return null;
        }
    }
}
