package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.ChangePasswordRequest;
import com.miguel.financemanager.security.JwtAuthenticationFilter;
import com.miguel.financemanager.service.AccountService;
import com.miguel.financemanager.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final CurrentUserService currentUserService;

    // Devolve um par de tokens novo para este dispositivo; as outras sessões terminam.
    @PutMapping("/password")
    public ResponseEntity<AuthResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @RequestAttribute(name = JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, required = false) Long sessionId) {
        return ResponseEntity.ok(accountService.changePassword(currentUserService.getCurrentUser(), request, sessionId));
    }
}
