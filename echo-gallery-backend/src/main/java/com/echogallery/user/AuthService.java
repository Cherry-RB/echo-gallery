package com.echogallery.user;

import com.echogallery.security.JwtService;

import jakarta.transaction.Transactional;

import com.echogallery.exception.UserAlreadyExistsException;
import com.echogallery.security.CustomUserDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.ZonedDateTime;

// 這裡會用到 SecurityConfig 裡宣告的 AuthenticationManager 來做驗證，以及 PasswordEncoder 來加密密碼
@Service
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    // 🚀 註冊邏輯
    public AuthDto.AuthResponse register(AuthDto.RegisterRequest request) {
        // 1. 防禦性檢查：
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("該 Email 已被註冊");
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("該使用者名稱已被使用");
        }

        // 2. 密碼雜湊化處理
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 3. 建立並儲存 User 實體
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(encodedPassword) // 存入雜湊值 💡
                .createdAt(ZonedDateTime.now())
                .updatedAt(ZonedDateTime.now())
                .showContentPreview(true) // 設定預設值
                .build();

        userRepository.save(user);

        // 4. 註冊成功後直接發放 Token
        return generateAuthResponse(user);
    }

    // 🚀 登入邏輯
    public AuthDto.AuthResponse login(AuthDto.LoginRequest request) {
        // 1. 驗證 (Spring Security 若失敗會拋出 AuthenticationException)
        // 讓 Spring Security 幫我們搞定密碼比對
        // 這裡會自動去呼叫你的 CustomUserDetailsService.loadUserByUsername()
        // 並用 BCryptPasswordEncoder 比對 request 的密碼與資料庫的 passwordHash
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        // 2. 從 SecurityContext 或直接用 email 撈取 User
        // 驗證成功後，撈出使用者並產生 Token
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email 或密碼錯誤"));

        return generateAuthResponse(user);
    }

    // 共用方法：封裝 Token 產生邏輯
    private AuthDto.AuthResponse generateAuthResponse(User user){
            CustomUserDetails userDetails = new CustomUserDetails(user);
        String jwtToken = jwtService.generateToken(userDetails);

        return AuthDto.AuthResponse.builder()
                .token(jwtToken)
                .username(user.getUsername())
                .email(user.getEmail())
                .build();
    }
}
