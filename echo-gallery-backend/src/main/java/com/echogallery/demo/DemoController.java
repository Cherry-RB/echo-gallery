package com.echogallery.demo;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.echogallery.user.AuthDto;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/auth/demo-sessions")
@RequiredArgsConstructor
public class DemoController {
    private final DemoSessionService demoSessionService;
    private final DemoProperties demoProperties;

    @PostMapping
    public ResponseEntity<AuthDto.AuthResponse> create(@Valid @RequestBody DemoSessionRequest request) {
        return ResponseEntity.ok(demoSessionService.create(request.library()));
    }

    @PostMapping("/reset")
    public ResponseEntity<AuthDto.AuthResponse> reset() {
        return ResponseEntity.ok(demoSessionService.resetCurrent());
    }

    @PostMapping("/feature-status")
    public ResponseEntity<Map<String, Boolean>> featureStatus() {
        return ResponseEntity.ok(Map.of("enabled", demoProperties.isEnabled()));
    }
}
