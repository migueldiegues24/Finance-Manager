package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.LoginRequest;
import com.miguel.financemanager.dto.RegisterRequest;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.security.AuthRateLimiter;
import com.miguel.financemanager.security.JwtService;
import com.miguel.financemanager.security.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String[] DEFAULT_CATEGORIES = {
            "Alimentação", "Transporte", "Casa", "Saúde", "Lazer", "Outros"
    };

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthRateLimiter authRateLimiter;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Já existe uma conta com este email");
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        userRepository.save(user);

        seedDefaultCategories(user);

        return issueTokens(user);
    }

    // O bloqueio é verificado antes da password (e antes do BCrypt): enquanto
    // dura, até a password certa recebe 429, exista ou não a conta.
    public AuthResponse login(LoginRequest request, String clientIp) {
        authRateLimiter.checkLogin(clientIp, request.getEmail());
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            authRateLimiter.loginFailed(clientIp, request.getEmail());
            throw new BadCredentialsException("Email ou password inválidos");
        }
        authRateLimiter.loginSucceeded(clientIp, request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Email ou password inválidos"));

        return issueTokens(user);
    }

    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.consumeRefreshToken(rawRefreshToken);
        return issueTokens(user);
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeToken(rawRefreshToken);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user.getEmail());
        String refreshToken = refreshTokenService.createRefreshToken(user);
        return new AuthResponse(accessToken, refreshToken);
    }

    private void seedDefaultCategories(User user) {
        for (String name : DEFAULT_CATEGORIES) {
            categoryRepository.save(Category.builder()
                    .user(user)
                    .name(name)
                    .isDefault(false)
                    .build());
        }

        // "Sem Categoria" é a única protegida: destino automático quando o
        // utilizador apaga outra categoria com transações ou regras associadas.
        categoryRepository.save(Category.builder()
                .user(user)
                .name("Sem Categoria")
                .isDefault(true)
                .build());
    }
}
