package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Limites de tentativas no login, registo e refresh, com o relógio controlado
// pelo teste. Contexto próprio (o Clock é substituído), com outra base H2.
// Os contadores são repostos antes de cada teste (AuthRateLimiterResetListener).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:ratelimit;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthRateLimitIntegrationTest {

    private static final String PASSWORD = "senha-de-teste-42";
    private static final String TOO_MANY = "Demasiadas tentativas. Tenta de novo daqui a 30 segundos.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private Clock clock;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-09-23T10:00:00Z");
        when(clock.instant()).thenAnswer(invocation -> now);
    }

    private void advance(Duration duration) {
        now = now.plus(duration);
    }

    // Conta criada diretamente, para não gastar o limite do registo.
    private void createUser(String email) {
        userRepository.save(User.builder().email(email).passwordHash(passwordEncoder.encode(PASSWORD)).build());
    }

    private ResultActions postJson(String path, Map<String, String> body, String ip) throws Exception {
        return mockMvc.perform(post(path)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions login(String email, String password, String ip) throws Exception {
        return postJson("/api/auth/login", Map.of("email", email, "password", password), ip);
    }

    private ResultActions register(String email, String password, String ip) throws Exception {
        return postJson("/api/auth/register", Map.of("email", email, "password", password), ip);
    }

    private ResultActions refresh(String token, String ip) throws Exception {
        return postJson("/api/auth/refresh", Map.of("refreshToken", token), ip);
    }

    private void failLogins(String email, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, "password-errada", ip).andExpect(status().isUnauthorized());
        }
    }

    // --- Login ---

    @Test
    void login_blocksAfterFiveFailures_evenWithRightPassword() throws Exception {
        createUser("alvo@teste.com");
        failLogins("alvo@teste.com", "198.51.100.1", 5);

        login("alvo@teste.com", PASSWORD, "198.51.100.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error").value(TOO_MANY));
    }

    @Test
    void login_retryAfterCountsDown_andUnlocksWhenTimePasses() throws Exception {
        createUser("espera@teste.com");
        failLogins("espera@teste.com", "198.51.100.1", 5);

        advance(Duration.ofSeconds(12));
        login("espera@teste.com", PASSWORD, "198.51.100.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "18"));

        advance(Duration.ofSeconds(18));
        login("espera@teste.com", PASSWORD, "198.51.100.1").andExpect(status().isOk());
    }

    @Test
    void login_backoffDoublesOnNextLock() throws Exception {
        createUser("backoff@teste.com");
        failLogins("backoff@teste.com", "198.51.100.1", 5);
        advance(Duration.ofSeconds(30));

        failLogins("backoff@teste.com", "198.51.100.1", 5);

        login("backoff@teste.com", PASSWORD, "198.51.100.1")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void login_successResetsIpAndEmailCounter() throws Exception {
        createUser("reposto@teste.com");
        failLogins("reposto@teste.com", "198.51.100.1", 4);
        login("reposto@teste.com", PASSWORD, "198.51.100.1").andExpect(status().isOk());

        failLogins("reposto@teste.com", "198.51.100.1", 4);

        login("reposto@teste.com", PASSWORD, "198.51.100.1").andExpect(status().isOk());
    }

    // Quem atualiza a página ou entra várias vezes seguidas nunca é bloqueado.
    @Test
    void login_repeatedValidLogins_areNeverBlocked() throws Exception {
        createUser("frequente@teste.com");

        for (int i = 0; i < 30; i++) {
            login("frequente@teste.com", PASSWORD, "198.51.100.1").andExpect(status().isOk());
        }
    }

    @Test
    void login_blocksIpAfterTwentyFailures_acrossDifferentEmails() throws Exception {
        String ip = "198.51.100.2";
        for (int i = 0; i < 20; i++) {
            login("email" + i + "@teste.com", "password-errada", ip).andExpect(status().isUnauthorized());
        }

        createUser("nova@teste.com");
        login("nova@teste.com", PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));
    }

    // Um login certo não repõe o contador por IP: senão, uma conta válida do
    // atacante servia para o limpar entre tentativas.
    @Test
    void login_successDoesNotResetIpCounter() throws Exception {
        String ip = "198.51.100.3";
        createUser("atacante@teste.com");
        for (int i = 0; i < 19; i++) {
            login("vitima" + i + "@teste.com", "password-errada", ip).andExpect(status().isUnauthorized());
        }
        login("atacante@teste.com", PASSWORD, ip).andExpect(status().isOk());

        login("outra-vitima@teste.com", "password-errada", ip).andExpect(status().isUnauthorized());

        login("atacante@teste.com", PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_usersOnDifferentIps_doNotBlockEachOther() throws Exception {
        createUser("bloqueado@teste.com");
        createUser("inocente@teste.com");

        for (int i = 0; i < 20; i++) {
            login("bloqueado@teste.com", "password-errada", "198.51.100.4");
        }
        login("bloqueado@teste.com", PASSWORD, "198.51.100.4").andExpect(status().isTooManyRequests());

        login("inocente@teste.com", PASSWORD, "203.0.113.50").andExpect(status().isOk());
        login("inocente@teste.com", "password-errada", "203.0.113.50").andExpect(status().isUnauthorized());
        // A chave é IP+email: o dono da conta, noutro IP, também não fica bloqueado.
        login("bloqueado@teste.com", PASSWORD, "203.0.113.51").andExpect(status().isOk());
    }

    // Nada na resposta distingue um email registado de um que não existe.
    @Test
    void login_responsesDoNotRevealWhetherEmailExists() throws Exception {
        createUser("existe@teste.com");

        MvcResult[] existing = new MvcResult[6];
        MvcResult[] missing = new MvcResult[6];
        for (int i = 0; i < 6; i++) {
            existing[i] = login("existe@teste.com", "password-errada", "198.51.100.5").andReturn();
            missing[i] = login("nao-existe@teste.com", "password-errada", "198.51.100.6").andReturn();
        }

        for (int i = 0; i < 6; i++) {
            assertThat(missing[i].getResponse().getStatus()).isEqualTo(existing[i].getResponse().getStatus());
            assertThat(missing[i].getResponse().getContentAsString())
                    .isEqualTo(existing[i].getResponse().getContentAsString());
            assertThat(missing[i].getResponse().getHeader("Retry-After"))
                    .isEqualTo(existing[i].getResponse().getHeader("Retry-After"));
        }
        assertThat(existing[5].getResponse().getStatus()).isEqualTo(429);
    }

    // --- Registo ---

    @Test
    void register_allowsTenPerHourPerIp_thenBlocksUntilWindowEnds() throws Exception {
        String ip = "198.51.100.7";
        for (int i = 0; i < 10; i++) {
            register("novo" + i + "@teste.com", PASSWORD, ip).andExpect(status().isOk());
            advance(Duration.ofMinutes(1));
        }

        register("novo10@teste.com", PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", String.valueOf(Duration.ofMinutes(50).toSeconds())));

        register("outro-ip@teste.com", PASSWORD, "203.0.113.60").andExpect(status().isOk());

        advance(Duration.ofMinutes(50));
        register("novo10@teste.com", PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void register_duplicateEmailAttemptsCount() throws Exception {
        String ip = "198.51.100.8";
        register("repetido@teste.com", PASSWORD, ip).andExpect(status().isOk());
        for (int i = 0; i < 9; i++) {
            register("repetido@teste.com", PASSWORD, ip).andExpect(status().isBadRequest());
        }

        register("repetido@teste.com", PASSWORD, ip).andExpect(status().isTooManyRequests());
    }

    // Quem está a escolher uma password não gasta o limite.
    @Test
    void register_rejectedPasswordsDoNotCount() throws Exception {
        String ip = "198.51.100.9";
        for (int i = 0; i < 15; i++) {
            register("fraca@teste.com", "password1234", ip)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value(
                            "Esta password é demasiado comum ou fácil de adivinhar. Escolhe outra."));
            register("curta@teste.com", "curta", ip).andExpect(status().isBadRequest());
        }

        register("fraca@teste.com", PASSWORD, ip).andExpect(status().isOk());
    }

    // --- Refresh ---

    @Test
    void refresh_blocksAfterTenInvalidTokens_thenUnlocks() throws Exception {
        createUser("sessao@teste.com");
        String refreshToken = objectMapper.readTree(login("sessao@teste.com", PASSWORD, "198.51.100.10")
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        String ip = "198.51.100.10";
        for (int i = 0; i < 10; i++) {
            refresh("token-inventado-" + i, ip).andExpect(status().isBadRequest());
        }

        // Até um token válido é recusado enquanto dura o bloqueio.
        refresh(refreshToken, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.error").value(TOO_MANY));
        refresh(refreshToken, "203.0.113.70").andExpect(status().isOk());

        advance(Duration.ofSeconds(30));
        for (int i = 0; i < 10; i++) {
            refresh("token-inventado-b" + i, ip).andExpect(status().isBadRequest());
        }
        refresh("token-inventado-c", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"));
    }

    @Test
    void refresh_validRotationsAreNeverBlocked() throws Exception {
        createUser("rotacao@teste.com");
        String ip = "198.51.100.11";
        String refreshToken = objectMapper.readTree(login("rotacao@teste.com", PASSWORD, ip)
                .andReturn().getResponse().getContentAsString()).get("refreshToken").asText();

        for (int i = 0; i < 25; i++) {
            MvcResult result = refresh(refreshToken, ip).andExpect(status().isOk()).andReturn();
            refreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                    .get("refreshToken").asText();
        }
    }
}
