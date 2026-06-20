package com.echogallery.user;

import lombok.*;

public class AuthDto {

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class RegisterRequest {
        private String username;
        private String email;
        private String password; // 前端傳來的明文密碼
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class LoginRequest {
        private String email;
        private String password;
    }

    @Getter @Builder
    @AllArgsConstructor
    public static class AuthResponse {
        private String token;
        private String username;
        private String email;
    }
}