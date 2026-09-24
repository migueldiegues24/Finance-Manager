package com.miguel.financemanager.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// Com o Tomcat real (o MockMvc não passa pelo parser de multipart, nem por
// corpos chunked ou lentos). Timeouts curtos para o teste ser rápido: 1 s sem
// bytes numa leitura (server.tomcat.connection-timeout) e 3 s no total para o
// corpo JSON chegar (request-body.read-timeout); em produção são 20 s e 30 s.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:serverlimits;DB_CLOSE_DELAY=-1",
        "server.tomcat.connection-timeout=1s",
        "request-body.read-timeout=3s"
})
@ActiveProfiles("test")
class RequestLimitsServerTest {

    private static final int KB = 1024;
    private static final String ORIGIN = "http://localhost:5173";
    private static final String BOUNDARY = "limites-boundary";

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private String token;

    @BeforeEach
    void registerUser() throws Exception {
        // Sem rollback aqui (servidor real): um email diferente por teste.
        String body = objectMapper.writeValueAsString(
                Map.of("email", "srv-" + UUID.randomUUID() + "@teste.com", "password", "senha-de-teste-42"));
        HttpResponse<String> response = send(json("/api/auth/register", HttpRequest.BodyPublishers.ofString(body)));
        assertThat(response.statusCode()).isEqualTo(200);
        token = objectMapper.readTree(response.body()).get("accessToken").asText();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .timeout(Duration.ofSeconds(20))
                .header("Origin", ORIGIN);
    }

    private HttpRequest json(String path, HttpRequest.BodyPublisher body) {
        return request(path).header("Content-Type", "application/json").POST(body).build();
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static byte[] multipartBody(int fileBytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(("--" + BOUNDARY + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"extrato.csv\"\r\n"
                + "Content-Type: text/csv\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        StringBuilder csv = new StringBuilder("data,descricao,valor\n");
        for (int i = 0; csv.length() < fileBytes - 40; i++) {
            csv.append("2026-09-").append(String.format("%02d", i % 28 + 1)).append(",COMPRA ").append(i).append(",-1.50\n");
        }
        out.writeBytes(csv.toString().getBytes(StandardCharsets.UTF_8));
        out.writeBytes(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private HttpResponse<String> upload(String path, byte[] body) throws Exception {
        return send(request(path)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build());
    }

    private static void assertCleanJsonError(HttpResponse<String> response, String message) {
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                type -> assertThat(type).startsWith("application/json"));
        assertThat(response.body()).contains(message).doesNotContain("Exception").doesNotContain("at com.");
        // Com CORS: sem este cabeçalho o browser escondia a mensagem ao frontend.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue(ORIGIN);
    }

    // --- Upload (multipart) ---

    @Test
    void uploadOver1MB_is413WithJsonMessage_not401or500() throws Exception {
        HttpResponse<String> response = upload("/api/imports/parse", multipartBody(1024 * KB + 200 * KB));

        assertThat(response.statusCode()).isEqualTo(413);
        assertCleanJsonError(response, "O ficheiro é demasiado grande (máximo 1 MB).");
    }

    // O multipart só tem os limites próprios: 100 KB (acima dos 32 KB do
    // corpo JSON, com Content-Length; ~3300 movimentos, abaixo dos 5000)
    // passa no upload.
    @Test
    void uploadUnder1MB_isNotLimitedByJsonBodyFilter() throws Exception {
        HttpResponse<String> response = upload("/api/imports/parse", multipartBody(100 * KB));

        assertThat(response.body()).doesNotContain("demasiado grande");
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // A isenção é só para o upload: um multipart noutro endpoint tem o limite
    // de 32 KB.
    @Test
    void multipartToOtherEndpoint_isLimitedTo32KB() throws Exception {
        HttpResponse<String> response = upload("/api/categories", multipartBody(40 * KB));

        assertThat(response.statusCode()).isEqualTo(413);
        assertCleanJsonError(response, "O pedido é demasiado grande (máximo 32 KB).");
    }

    // --- Corpo JSON sem Content-Length ---

    @Test
    void chunkedJsonOver32KB_is413() throws Exception {
        byte[] body = ("{\"email\":\"a@teste.com\",\"password\":\"x\",\"pad\":\"" + "p".repeat(40 * KB) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        // ofInputStream não sabe o tamanho: o pedido segue em chunks, sem Content-Length.
        HttpResponse<String> response = send(json("/api/auth/login",
                HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))));

        assertThat(response.statusCode()).isEqualTo(413);
        assertCleanJsonError(response, "O pedido é demasiado grande (máximo 32 KB).");
    }

    @Test
    void chunkedJsonUnderLimit_works() throws Exception {
        byte[] body = "{\"email\":\"ninguem@teste.com\",\"password\":\"errada123\"}".getBytes(StandardCharsets.UTF_8);
        HttpResponse<String> response = send(json("/api/auth/login",
                HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(body))));

        assertThat(response.statusCode()).isEqualTo(401);
    }

    // --- Timeouts ---

    private Socket openChunkedLogin() throws IOException {
        Socket socket = new Socket("localhost", port);
        socket.setSoTimeout(15_000);
        OutputStream out = socket.getOutputStream();
        out.write(("POST /api/auth/login HTTP/1.1\r\nHost: localhost\r\n"
                + "Content-Type: application/json\r\nTransfer-Encoding: chunked\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.flush();
        return socket;
    }

    private static String statusLine(InputStream in) throws IOException {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII)).readLine();
    }

    // Um byte a cada 300 ms nunca atinge o timeout por leitura (1 s), mas o
    // limite total do corpo (3 s) corta o pedido com 408.
    @Test
    void slowDripBody_isCutByTotalDeadline_with408() throws Exception {
        long start = System.nanoTime();
        try (Socket socket = openChunkedLogin()) {
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            byte[] chunk = "1\r\n \r\n".getBytes(StandardCharsets.US_ASCII);
            out.write("1\r\n{\r\n".getBytes(StandardCharsets.US_ASCII));
            while (in.available() == 0 && System.nanoTime() - start < Duration.ofSeconds(12).toNanos()) {
                try {
                    out.write(chunk);
                    out.flush();
                } catch (IOException closedByServer) {
                    break;
                }
                Thread.sleep(300);
            }

            String status = statusLine(in);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            assertThat(status).startsWith("HTTP/1.1 408");
            assertThat(elapsed).isBetween(Duration.ofMillis(2_900), Duration.ofSeconds(8));
        }
    }

    // Sem nenhum byte do corpo, o timeout por leitura do Tomcat (1 s aqui,
    // 20 s em produção) solta a ligação: não fica aberta indefinidamente.
    @Test
    void idleBody_isCutByTomcatReadTimeout() throws Exception {
        long start = System.nanoTime();
        try (Socket socket = openChunkedLogin()) {
            String status = statusLine(socket.getInputStream());
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

            // Resposta de erro ou ligação fechada (null); nunca ficar pendurado.
            if (status != null) {
                assertThat(status).matches("HTTP/1\\.1 4\\d\\d.*");
            }
            assertThat(elapsed).isBetween(Duration.ofMillis(900), Duration.ofSeconds(8));
        }
    }
}
