package com.echogallery.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer{
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 1. 允許所有 API 路徑
                .allowedOrigins("http://localhost:5173") // 2. 允許的前端來源（可填多個）
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 3. 允許的 HTTP 方法
                .allowedHeaders("*") // 4. 允許的 Header 頭訊息
                .allowCredentials(true) // 5. 是否允許攜帶 Cookie / 認證資訊
                .maxAge(3600); // 6. 預檢請求（Preflight）的快取時間（秒）
    }
}
