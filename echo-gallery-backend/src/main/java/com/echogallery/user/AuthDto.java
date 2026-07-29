package com.echogallery.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

public class AuthDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {

        @NotBlank(message = "使用者名稱不能為空")
        @Size(min = 2, max = 50, message = "使用者名稱長度必須介於 2 到 50 個字元之間")
        private String username;

        @NotBlank(message = "Email 不能為空")
        @Email(message = "Email 格式不正確")
        private String email;

        @NotBlank(message = "密碼不能為空")
        @Size(min = 6, message = "密碼長度至少需要 6 個字元")
        private String password; // 前端傳來的明文密碼
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {

        @NotBlank(message = "Email 不能為空")
        @Email(message = "Email 格式不正確")
        private String email;

        @NotBlank(message = "密碼不能為空")
        private String password;
    }

    @Getter @Builder
    @AllArgsConstructor
    public static class AuthResponse {
        private Long id;
        private String token;
        private String username;
        private String email;
    }
}
