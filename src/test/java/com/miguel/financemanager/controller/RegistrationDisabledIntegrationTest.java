package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.User;
import com.miguel.financemanager.repository.CategoryRepository;
import com.miguel.financemanager.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Contexto próprio com o registo desligado. Usa outra base H2 em memória para
// o create-drop deste contexto não mexer no esquema da "testdb" partilhada
// pelos outros testes de integração.
@SpringBootTest(properties = {
        "registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:registrationdisabled;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistrationDisabledIntegrationTest {

    private static final String DISABLED_MESSAGE = "O registo de novas contas está desativado.";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void register_returns403AndWritesNothing() throws Exception {
        long usersBefore = userRepository.count();
        long categoriesBefore = categoryRepository.count();

        String body = objectMapper.writeValueAsString(
                Map.of("email", "novo@teste.com", "password", "senha-de-teste-42"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DISABLED_MESSAGE));

        assertThat(userRepository.existsByEmail("novo@teste.com")).isFalse();
        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(categoryRepository.count()).isEqualTo(categoriesBefore);
    }

    // A flag é verificada antes de o corpo ser lido: inválido ou ilegível dá 403, não 400.
    @Test
    void register_returns403RegardlessOfBody() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"password\": \"x\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DISABLED_MESSAGE));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("isto não é json"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DISABLED_MESSAGE));

        mockMvc.perform(post("/api/auth/register"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(DISABLED_MESSAGE));
    }

    @Test
    void login_stillWorksForExistingAccounts() throws Exception {
        // Conta criada diretamente, como se já existisse antes de desligar o registo.
        userRepository.save(User.builder()
                .email("existente@teste.com")
                .passwordHash(passwordEncoder.encode("senha-de-teste-42"))
                .build());

        String loginBody = objectMapper.writeValueAsString(
                Map.of("email", "existente@teste.com", "password", "senha-de-teste-42"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists());
    }

    @Test
    void registrationStatus_reportsDisabled() throws Exception {
        mockMvc.perform(get("/api/auth/registration"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
