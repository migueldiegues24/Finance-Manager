package com.miguel.financemanager.controller;

import com.miguel.financemanager.config.RegistrationConfig;
import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.LoginRequest;
import com.miguel.financemanager.dto.RefreshRequest;
import com.miguel.financemanager.dto.RegisterRequest;
import com.miguel.financemanager.security.ClientIpResolver;
import com.miguel.financemanager.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RegistrationConfig registrationConfig;
    private final ClientIpResolver clientIpResolver;

    // Público: o frontend usa-o para esconder o formulário de registo.
    @GetMapping("/registration")
    public ResponseEntity<Map<String, Boolean>> registration() {
        return ResponseEntity.ok(Map.of("enabled", registrationConfig.isRegistrationEnabled()));
    }

    // Com o registo desligado, o RegistrationConfig recusa o pedido antes de chegar aqui.

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.register(request, clientIpResolver.resolve(http)));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.login(request, clientIpResolver.resolve(http)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest http) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken(), clientIpResolver.resolve(http)));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }
}
