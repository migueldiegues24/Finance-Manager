package com.miguel.financemanager.service;

import com.miguel.financemanager.dto.AuthResponse;
import com.miguel.financemanager.dto.LoginRequest;
import com.miguel.financemanager.dto.RegisterRequest;
import com.miguel.financemanager.entity.Category;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.exception.TooManyAttemptsException;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.UserRepository;
import com.miguel.financemanager.security.AuthRateLimiter;
import com.miguel.financemanager.security.JwtService;
import com.miguel.financemanager.security.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AuthRateLimiter authRateLimiter;

    @InjectMocks
    private AuthService authService;

    @Test
    void register_rejectsExistingEmail() {
        when(userRepository.existsByEmail("miguel@teste.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("senha-de-teste-42");

        assertThatThrownBy(() -> authService.register(request, "203.0.113.7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Já existe");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_encodesPasswordSeedsSevenCategoriesAndIssuesTokens() {
        when(userRepository.existsByEmail("miguel@teste.com")).thenReturn(false);
        when(passwordEncoder.encode("senha-de-teste-42")).thenReturn("hashed");
        when(jwtService.generateAccessToken(anyString())).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(any(User.class))).thenReturn("refresh-token");

        RegisterRequest request = new RegisterRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("senha-de-teste-42");

        AuthResponse response = authService.register(request, "203.0.113.7");

        verify(authRateLimiter).registerAttempted("203.0.113.7");

        verify(passwordEncoder).encode("senha-de-teste-42");
        verify(userRepository).save(any(User.class));
        // 6 categorias sugeridas + "Sem Categoria"
        verify(categoryRepository, times(7)).save(any(Category.class));

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void login_wrapsAuthenticationFailureAsBadCredentials() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        LoginRequest request = new LoginRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("errada");

        assertThatThrownBy(() -> authService.login(request, "203.0.113.7"))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("inválidos");

        verify(authRateLimiter).checkLogin("203.0.113.7", "miguel@teste.com");
        verify(authRateLimiter).loginFailed("203.0.113.7", "miguel@teste.com");
        verify(authRateLimiter, never()).loginSucceeded(anyString(), anyString());
    }

    @Test
    void login_whenBlocked_doesNotCheckPassword() {
        doThrow(new TooManyAttemptsException(30)).when(authRateLimiter).checkLogin("203.0.113.7", "miguel@teste.com");

        LoginRequest request = new LoginRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("senha-de-teste-42");

        assertThatThrownBy(() -> authService.login(request, "203.0.113.7"))
                .isInstanceOf(TooManyAttemptsException.class);
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void register_rejectsCommonPassword_withoutCountingTowardLimit() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("Password1234");

        assertThatThrownBy(() -> authService.register(request, "203.0.113.7"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("demasiado comum");

        verify(authRateLimiter, never()).registerAttempted(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void refresh_recordsFailureForInvalidToken() {
        when(refreshTokenService.consumeRefreshToken("bad")).thenThrow(new IllegalArgumentException("Refresh token inválido"));

        assertThatThrownBy(() -> authService.refresh("bad", "203.0.113.7"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(authRateLimiter).refreshFailed("203.0.113.7");
    }

    @Test
    void login_succeedsAndIssuesTokens() {
        User user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(userRepository.findByEmail("miguel@teste.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken("miguel@teste.com")).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("refresh-token");

        LoginRequest request = new LoginRequest();
        request.setEmail("miguel@teste.com");
        request.setPassword("senha-de-teste-42");

        AuthResponse response = authService.login(request, "203.0.113.7");

        verify(authRateLimiter).loginSucceeded("203.0.113.7", "miguel@teste.com");
        verify(authRateLimiter, never()).loginFailed(anyString(), anyString());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void refresh_consumesOldTokenAndIssuesNewPair() {
        User user = User.builder().id(1L).email("miguel@teste.com").passwordHash("hash").build();
        when(refreshTokenService.consumeRefreshToken("old-raw-token")).thenReturn(user);
        when(jwtService.generateAccessToken("miguel@teste.com")).thenReturn("new-access");
        when(refreshTokenService.createRefreshToken(user)).thenReturn("new-refresh");

        AuthResponse response = authService.refresh("old-raw-token", "203.0.113.7");

        verify(refreshTokenService).consumeRefreshToken("old-raw-token");
        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void logout_revokesGivenToken() {
        authService.logout("some-raw-token");

        verify(refreshTokenService).revokeToken("some-raw-token");
    }
}
