package com.miguel.financemanager.controller;

import com.miguel.financemanager.dto.AccountExport;
import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.ChangePasswordRequest;
import com.miguel.financemanager.dto.DeleteAccountRequest;
import com.miguel.financemanager.dto.SessionResponse;
import com.miguel.financemanager.security.JwtAuthenticationFilter;
import com.miguel.financemanager.service.AccountService;
import com.miguel.financemanager.service.CurrentUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> sessions(
            @RequestAttribute(name = JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, required = false) Long sessionId) {
        return ResponseEntity.ok(accountService.listSessions(currentUserService.getCurrentUser(), sessionId));
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> revokeSession(@PathVariable Long id) {
        accountService.revokeSession(currentUserService.getCurrentUser(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions/revoke-others")
    public ResponseEntity<Map<String, Integer>> revokeOtherSessions(
            @RequestAttribute(name = JwtAuthenticationFilter.SESSION_ID_ATTRIBUTE, required = false) Long sessionId) {
        int revoked = accountService.revokeOtherSessions(currentUserService.getCurrentUser(), sessionId);
        return ResponseEntity.ok(Map.of("revoked", revoked));
    }

    // Resposta potencialmente grande a SAIR: o limite de corpo (RequestBodyLimitFilter)
    // só se aplica ao que o cliente envia. O custo é travado por um limite de
    // pedidos por hora (AccountRateLimiter), não por tamanho.
    @GetMapping("/export")
    public ResponseEntity<AccountExport> export() {
        String filename = "finance-manager-" + LocalDate.now() + ".json";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(accountService.export(currentUserService.getCurrentUser()));
    }

    // A password atual vai no corpo, como confirmação.
    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@Valid @RequestBody DeleteAccountRequest request) {
        accountService.deleteAccount(currentUserService.getCurrentUser(), request);
        return ResponseEntity.noContent().build();
    }
}
