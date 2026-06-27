package com.echogallery.security.error;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.echogallery.exception.ErrorResponse;

import java.io.IOException;

@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException, ServletException {

        // 1. 設定 HTTP 回應標頭為 JSON 格式與 UTF-8 編碼
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401

        // 2. 建立標準錯誤物件
        ErrorResponse errorResponse = new ErrorResponse(
                "認證失敗：無效的 Token 或憑證已過期，請重新登入",
                HttpServletResponse.SC_UNAUTHORIZED
        );

        // 3. 利用 ObjectMapper 將物件轉為 JSON 字串寫回前端
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
