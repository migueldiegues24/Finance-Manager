package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.ChangePasswordRequest;
import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.security.AccountRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountRateLimiter accountRateLimiter;
    private final AuthService authService;

    // Revoga todas as sessões (a atual incluída) e devolve um par novo a este
    // dispositivo, no mesmo modo da sessão atual: os outros dispositivos têm
    // de voltar a entrar. Os access tokens já emitidos continuam válidos até
    // expirarem (máx. 15 min; ver docs/backlog.md).
    @Transactional
    public AuthResponse changePassword(User user, ChangePasswordRequest request, Long currentSessionId) {
        verifyPassword(user, request.getCurrentPassword());

        PasswordPolicy.validate(user.getEmail(), request.getNewPassword());
        if (request.getNewPassword().equals(request.getCurrentPassword())) {
            throw new IllegalArgumentException("A nova password tem de ser diferente da atual.");
        }

        boolean rememberMe = currentSession(user, currentSessionId)
                .map(RefreshToken::isRememberMe)
                .orElse(false);

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUser(user);

        return authService.issueTokens(user, rememberMe);
    }

    // O bloqueio é verificado antes do BCrypt: enquanto dura, até a password
    // certa recebe 429. Password errada dá 400 e não 401, que o frontend trata
    // como sessão expirada.
    private void verifyPassword(User user, String password) {
        accountRateLimiter.checkPassword(user.getId());
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            accountRateLimiter.passwordFailed(user.getId());
            throw new IllegalArgumentException("A password atual está incorreta.");
        }
        accountRateLimiter.passwordSucceeded(user.getId());
    }

    // Sessão do pedido (claim "sid"), só se for deste utilizador.
    private Optional<RefreshToken> currentSession(User user, Long sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByIdAndUser(sessionId, user);
    }
}
