package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.AccountExport;
import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.ChangePasswordRequest;
import com.miguel.financemanager.dto.SessionResponse;
import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.exception.ResourceNotFoundException;
import com.miguel.financemanager.repository.CategorizationRuleRepository;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import com.miguel.financemanager.repository.StatementImportRepository;
import com.miguel.financemanager.repository.TransactionRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.security.AccountRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CategoryRepository categoryRepository;
    private final CategorizationRuleRepository ruleRepository;
    private final StatementImportRepository importRepository;
    private final TransactionRepository transactionRepository;
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

    // A sessão atual (claim "sid") vem assinalada; tokens sem a claim não
    // assinalam nenhuma.
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(User user, Long currentSessionId) {
        return activeSessions(user).stream()
                .map(token -> new SessionResponse(
                        token.getId(),
                        token.getCreatedAt(),
                        token.getExpiresAt(),
                        token.isRememberMe() ? SessionResponse.Mode.REMEMBERED : SessionResponse.Mode.SHORT,
                        token.getId().equals(currentSessionId)))
                .toList();
    }

    // 404 se a sessão não existir, for de outra conta ou já não estiver ativa
    // (a mesma resposta nos três casos). Pode ser a atual: equivale a sair.
    @Transactional
    public void revokeSession(User user, long sessionId) {
        RefreshToken token = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .filter(this::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Sessão não encontrada"));
        token.setRevoked(true);
        refreshTokenRepository.save(token);
    }

    // Devolve quantas sessões terminou. Sem a claim "sid" não se sabe qual é
    // a atual, e revogar todas terminaria também esta: recusa.
    @Transactional
    public int revokeOtherSessions(User user, Long currentSessionId) {
        if (currentSessionId == null) {
            throw new IllegalArgumentException("Não foi possível identificar a sessão atual. Volta a entrar e tenta de novo.");
        }
        return refreshTokenRepository.revokeAllByUserExcept(user, currentSessionId);
    }

    // Tudo lido de uma vez, em memória: com os volumes desta app (milhares de
    // movimentos) são poucos MB. As categorias e importações ficam no contexto
    // de persistência, por isso ler as das regras e transações não faz consultas.
    @Transactional(readOnly = true)
    public AccountExport export(User user) {
        accountRateLimiter.exportRequested(user.getId());

        List<AccountExport.CategoryEntry> categories = categoryRepository.findByUserOrderByIsDefaultAscIdAsc(user)
                .stream()
                .map(c -> new AccountExport.CategoryEntry(c.getId(), c.getName(), c.getColor(), c.isDefault(),
                        c.getCreatedAt()))
                .toList();
        List<AccountExport.RuleEntry> rules = ruleRepository.findByUserOrderByPriorityAsc(user).stream()
                .map(r -> new AccountExport.RuleEntry(r.getId(), r.getKeyword(), r.getCategory().getId(),
                        r.getCategory().getName(), r.getPriority()))
                .toList();
        List<AccountExport.ImportEntry> imports = importRepository.findByUserOrderByIdAsc(user).stream()
                .map(i -> new AccountExport.ImportEntry(i.getId(), i.getFilename(), i.getBank().name(),
                        i.getImportedAt()))
                .toList();
        List<AccountExport.TransactionEntry> transactions =
                transactionRepository.findByUserOrderByTransactionDateAscIdAsc(user).stream()
                        .map(t -> new AccountExport.TransactionEntry(t.getId(), t.getTransactionDate(),
                                t.getMovementDate(), t.getDescription(), t.getAmount(), t.getBalanceAfter(),
                                t.getCategory().getId(), t.getCategory().getName(),
                                t.getStatementImport().getId(), t.getCreatedAt()))
                        .toList();

        return new AccountExport(
                Instant.now(),
                new AccountExport.Profile(user.getEmail(), user.getCreatedAt()),
                categories, rules, imports, transactions);
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

    private List<RefreshToken> activeSessions(User user) {
        return refreshTokenRepository.findByUserAndRevokedFalseAndExpiresAtAfterOrderByCreatedAtDesc(user, Instant.now());
    }

    private boolean isActive(RefreshToken token) {
        return !token.isRevoked() && token.getExpiresAt().isAfter(Instant.now());
    }

    // Sessão do pedido (claim "sid"), só se for deste utilizador.
    private Optional<RefreshToken> currentSession(User user, Long sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        return refreshTokenRepository.findByIdAndUser(sessionId, user);
    }
}
