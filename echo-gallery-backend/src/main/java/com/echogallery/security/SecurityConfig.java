package com.echogallery.security;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.echogallery.security.error.CustomAccessDeniedHandler;
import com.echogallery.security.error.CustomAuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint; // 💡 注入 401 處理器
    private final CustomAccessDeniedHandler accessDeniedHandler;         // 💡 注入 403 處理器
    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthFilter,
        CustomAuthenticationEntryPoint authenticationEntryPoint,
        CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 開啟 CORS 支援，並套用我們下方寫的配置
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 1. 前後端分離架構，關閉 CSRF 防禦
                .csrf(AbstractHttpConfigurer::disable)
                // 2. 設定路由權限管制
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                         // 註冊、登入端點一律放行
                        .anyRequest().authenticated()               // 其餘所有 API（包含 Card 控制器）強制登入
                )
                // 補上內建的登出設定
                .logout(logout -> logout
                    .logoutUrl("/api/auth/logout") // 指定登出的 API 路徑
                    .logoutSuccessHandler((request, response, authentication) -> {
                        String username = (authentication != null) ? authentication.getName() : "未知用戶";
                        logger.info("用戶 {} 已於本地端成功登出，清除安全上下文。", username);

                        // 當登出成功時，不跳轉網頁，而是返回 200 OK 狀態碼與 JSON 訊息給前端
                        response.setStatus(HttpServletResponse.SC_OK);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"message\": \"後端登出成功 Logout successful\"}");
                    })
                )
                // 3. 關閉 Session 狀態，全面改為無狀態 (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 在這裡配置自訂的異常處理器
                .exceptionHandling(exception -> exception
                .authenticationEntryPoint(authenticationEntryPoint) // 管 401
                .accessDeniedHandler(accessDeniedHandler)           // 管 403
                )
                // 4. 將自訂的 JWT 攔截器掛載到登出過濾器（LogoutFilter）之前，確保登出時能先解析出用戶身分
                .addFilterBefore(jwtAuthFilter, LogoutFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 💡 密碼加密元件：未來用戶註冊、登入比對都會自動調用它
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 定義 CORS 的白名單規則
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允許你的前端 Vue 3 埠號存取
        configuration.setAllowedOrigins(List.of("http://localhost:5173", frontendUrl));

        // 允許所有 HTTP 方法 (GET, POST, PUT, DELETE, OPTIONS)
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // 允許前端帶過來的所有 Headers (如 Authorization, Content-Type)
        configuration.setAllowedHeaders(List.of("*"));

        // 允許前端攜帶 Cookie 或認證憑證
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 對後端所有的 API 路徑（/**）都套用此 CORS 規則
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
