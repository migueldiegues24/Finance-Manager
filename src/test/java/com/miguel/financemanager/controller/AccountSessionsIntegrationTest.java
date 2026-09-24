package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miguel.financemanager.entity.RefreshToken;
import com.miguel.financemanager.repository.RefreshTokenRepository;
import com.miguel.financemanager.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Sessões ativas da conta: listar (modo, atual assinalada, nunca o token),
// terminar uma (só as próprias; 404 para as alheias) e terminar todas as
// outras (a do pedido fica).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountSessionsIntegrationTest {

    private static final String EMAIL = "sessoes@teste.com";
    private static final String OTHER_EMAIL = "sessoes-b@teste.com";
    private static final String PASSWORD = "senha-de-teste-42";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void registerUsers() throws Exception {
        for (String email : List.of(EMAIL, OTHER_EMAIL)) {
            send(post("/api/auth/register"), null, Map.of("email", email, "password", PASSWORD))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void sessions_withoutToken_return401() throws Exception {
        send(get("/api/account/sessions"), null, null).andExpect(status().isUnauthorized());
        send(delete("/api/account/sessions/1"), null, null).andExpect(status().isUnauthorized());
        send(post("/api/account/sessions/revoke-others"), null, null).andExpect(status().isUnauthorized());
    }

    @Test
    void list_showsOnlyActiveSessionsOfThisUser_withModeAndCurrentFlag() throws Exception {
        JsonNode shortSession = login(EMAIL, false);
        JsonNode remembered = login(EMAIL, true);
        JsonNode other = login(OTHER_EMAIL, true);

        JsonNode list = sessions(remembered);

        // A do registo também conta: 3 sessões.
        assertThat(ids(list)).hasSize(3).contains(sid(shortSession), sid(remembered));
        assertThat(ids(list)).doesNotContain(sid(other));
        assertThat(entry(list, sid(shortSession)).get("mode").asText()).isEqualTo("SHORT");
        assertThat(entry(list, sid(remembered)).get("mode").asText()).isEqualTo("REMEMBERED");
        assertThat(entry(list, sid(remembered)).get("current").asBoolean()).isTrue();
        assertThat(entry(list, sid(shortSession)).get("current").asBoolean()).isFalse();

        // Só os campos previstos; nada do token nem do hash.
        List<String> fields = new ArrayList<>();
        list.get(0).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "createdAt", "expiresAt", "mode", "current");
        assertThat(list.toString()).doesNotContain(remembered.get("refreshToken").asText());
    }

    @Test
    void list_excludesRevokedAndExpiredSessions() throws Exception {
        JsonNode current = login(EMAIL, false);
        JsonNode loggedOut = login(EMAIL, false);
        JsonNode expired = login(EMAIL, false);

        send(post("/api/auth/logout"), null, Map.of("refreshToken", loggedOut.get("refreshToken").asText()))
                .andExpect(status().isNoContent());
        RefreshToken stored = refreshTokenRepository.findById(sid(expired)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        refreshTokenRepository.saveAndFlush(stored);

        assertThat(ids(sessions(current))).doesNotContain(sid(loggedOut), sid(expired)).contains(sid(current));
    }

    @Test
    void list_afterRefresh_marksTheRenewedSessionAsCurrent() throws Exception {
        JsonNode pair = json(refresh(login(EMAIL, false)).andExpect(status().isOk()));

        assertThat(entry(sessions(pair), sid(pair)).get("current").asBoolean()).isTrue();
    }

    @Test
    void revoke_ownSession_endsIt() throws Exception {
        JsonNode current = login(EMAIL, false);
        JsonNode phone = login(EMAIL, true);

        send(delete("/api/account/sessions/" + sid(phone)), access(current), null)
                .andExpect(status().isNoContent());

        assertThat(ids(sessions(current))).doesNotContain(sid(phone));
        refresh(phone).andExpect(status().isBadRequest());
        refresh(current).andExpect(status().isOk());
    }

    @Test
    void revoke_otherUsersSession_returns404AndLeavesItActive() throws Exception {
        JsonNode mine = login(EMAIL, false);
        JsonNode theirs = login(OTHER_EMAIL, false);

        send(delete("/api/account/sessions/" + sid(theirs)), access(mine), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Sessão não encontrada"));

        assertThat(refreshTokenRepository.findById(sid(theirs)).orElseThrow().isRevoked()).isFalse();
        refresh(theirs).andExpect(status().isOk());
    }

    @Test
    void revoke_missingOrAlreadyRevokedSession_returns404() throws Exception {
        JsonNode current = login(EMAIL, false);
        JsonNode other = login(EMAIL, false);

        send(delete("/api/account/sessions/999999"), access(current), null)
                .andExpect(status().isNotFound());

        send(delete("/api/account/sessions/" + sid(other)), access(current), null)
                .andExpect(status().isNoContent());
        send(delete("/api/account/sessions/" + sid(other)), access(current), null)
                .andExpect(status().isNotFound());
    }

    @Test
    void revokeOthers_keepsOnlyCurrentSession_andLeavesOtherUsersAlone() throws Exception {
        JsonNode current = login(EMAIL, false);
        JsonNode phone = login(EMAIL, true);
        JsonNode tablet = login(EMAIL, false);
        JsonNode theirs = login(OTHER_EMAIL, false);

        // A do registo, o telemóvel e o tablet.
        send(post("/api/account/sessions/revoke-others"), access(current), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revoked").value(3));

        assertThat(ids(sessions(current))).containsExactly(sid(current));
        refresh(phone).andExpect(status().isBadRequest());
        refresh(tablet).andExpect(status().isBadRequest());
        refresh(current).andExpect(status().isOk());
        refresh(theirs).andExpect(status().isOk());
    }

    // Access token emitido antes de haver a claim "sid" (ex.: logo a seguir ao deploy).
    @Test
    void tokenWithoutSessionClaim_listsNoCurrentAndCannotRevokeOthers() throws Exception {
        JsonNode phone = login(EMAIL, true);
        String legacy = Jwts.builder()
                .subject(EMAIL)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .compact();

        JsonNode list = json(send(get("/api/account/sessions"), legacy, null).andExpect(status().isOk()));
        list.forEach(node -> assertThat(node.get("current").asBoolean()).isFalse());

        send(post("/api/account/sessions/revoke-others"), legacy, null)
                .andExpect(status().isBadRequest());
        refresh(phone).andExpect(status().isOk());
    }

    private JsonNode login(String email, boolean rememberMe) throws Exception {
        return json(send(post("/api/auth/login"), null,
                Map.of("email", email, "password", PASSWORD, "rememberMe", rememberMe))
                .andExpect(status().isOk()));
    }

    private ResultActions refresh(JsonNode pair) throws Exception {
        return send(post("/api/auth/refresh"), null, Map.of("refreshToken", pair.get("refreshToken").asText()));
    }

    private JsonNode sessions(JsonNode pair) throws Exception {
        return json(send(get("/api/account/sessions"), access(pair), null).andExpect(status().isOk()));
    }

    private static List<Long> ids(JsonNode list) {
        List<Long> ids = new ArrayList<>();
        list.forEach(node -> ids.add(node.get("id").asLong()));
        return ids;
    }

    private static JsonNode entry(JsonNode list, long id) {
        for (JsonNode node : list) {
            if (node.get("id").asLong() == id) return node;
        }
        throw new AssertionError("Sessão " + id + " não está na lista");
    }

    private long sid(JsonNode pair) {
        return jwtService.extractSessionId(access(pair));
    }

    private static String access(JsonNode pair) {
        return pair.get("accessToken").asText();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, String accessToken, Object body)
            throws Exception {
        if (accessToken != null) {
            request.header("Authorization", "Bearer " + accessToken);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
