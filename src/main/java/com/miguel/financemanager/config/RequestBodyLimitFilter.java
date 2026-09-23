package com.miguel.financemanager.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Limite de tamanho (e de tempo) do corpo dos pedidos. Nem o Tomcat nem o
// Jackson limitam um corpo JSON por omissão (max-http-form-post-size só se
// aplica a formulários), e os endpoints de /api/auth são públicos.
//
//  - 32 KB por omissão: o maior pedido normal (registo, regra, categoria)
//    tem menos de 1 KB.
//  - 2 MB no POST /api/imports/confirm: ~250 bytes por movimento, o que dá
//    folga para os 5000 movimentos do limite do extrato.
//  - O upload multipart (POST /api/imports/parse) NÃO passa por aqui: tem os
//    limites próprios do spring.servlet.multipart e o Tomcat lê-o sem usar
//    este wrapper. Só é isento com esse path exato e Content-Type multipart;
//    qualquer outro pedido fica com o limite de 32 KB.
//
// Com Content-Length acima do limite, responde 413 sem ler o corpo. Sem
// Content-Length (chunked), conta os bytes à medida que são lidos. Também
// impõe um tempo máximo desde o início do pedido até o corpo acabar de
// chegar (408): o timeout do Tomcat (server.tomcat.connection-timeout) é por
// leitura, por isso um cliente que envie um byte de vez em quando, sempre
// antes desse timeout, prenderia a ligação e uma thread indefinidamente.
//
// Corre na cadeia do Spring Security logo a seguir ao CorsFilter, para as
// respostas 413/408 levarem os cabeçalhos CORS (senão o browser não mostra a
// mensagem) e para cobrir também os pedidos não autenticados.
public class RequestBodyLimitFilter extends OncePerRequestFilter {

    public static final long DEFAULT_LIMIT_BYTES = 32 * 1024;
    public static final long IMPORT_CONFIRM_LIMIT_BYTES = 2 * 1024 * 1024;

    private static final String IMPORT_CONFIRM_PATH = "/api/imports/confirm";
    private static final String IMPORT_PARSE_PATH = "/api/imports/parse";

    private final Duration readTimeout;

    public RequestBodyLimitFilter(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (isMultipartUpload(request)) {
            chain.doFilter(request, response);
            return;
        }

        long limit = limitFor(request);
        if (request.getContentLengthLong() > limit) {
            writeError(response, HttpStatus.CONTENT_TOO_LARGE, tooLargeMessage(limit));
            return;
        }

        try {
            chain.doFilter(new LimitedRequest(request, limit, readTimeout), response);
        } catch (IOException | ServletException | RuntimeException e) {
            // Rede de segurança: normalmente o GlobalExceptionHandler já
            // respondeu; isto só apanha o que escapar antes do controller.
            RequestBodyException cause = RequestBodyException.findIn(e);
            if (cause == null || response.isCommitted()) {
                throw e;
            }
            writeError(response, cause.status(), cause.getMessage());
        }
    }

    private static String path(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private static boolean isMultipartUpload(HttpServletRequest request) {
        String contentType = request.getContentType();
        return "POST".equals(request.getMethod())
                && IMPORT_PARSE_PATH.equals(path(request))
                && contentType != null
                && contentType.toLowerCase().startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    }

    private static long limitFor(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && IMPORT_CONFIRM_PATH.equals(path(request))
                ? IMPORT_CONFIRM_LIMIT_BYTES
                : DEFAULT_LIMIT_BYTES;
    }

    static String tooLargeMessage(long limit) {
        String size = limit >= 1024 * 1024 ? (limit / (1024 * 1024)) + " MB" : (limit / 1024) + " KB";
        return "O pedido é demasiado grande (máximo " + size + ").";
    }

    private static void writeError(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.resetBuffer();
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // As mensagens são fixas (sem aspas nem barras), não precisam de escape.
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }

    // Erro ao ler o corpo: demasiado grande (413) ou demasiado lento (408).
    // É uma IOException porque é lançada de dentro de InputStream.read.
    public static class RequestBodyException extends IOException {

        private final HttpStatus status;

        RequestBodyException(HttpStatus status, String message) {
            super(message);
            this.status = status;
        }

        public HttpStatus status() {
            return status;
        }

        public static RequestBodyException findIn(Throwable error) {
            for (Throwable t = error; t != null; t = t.getCause()) {
                if (t instanceof RequestBodyException found) {
                    return found;
                }
            }
            return null;
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {

        private final LimitedInputStream stream;

        LimitedRequest(HttpServletRequest request, long limit, Duration readTimeout) {
            super(request);
            this.stream = new LimitedInputStream(request, limit, readTimeout);
        }

        @Override
        public ServletInputStream getInputStream() {
            return stream;
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(stream, charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {

        private final HttpServletRequest request;
        private final long limit;
        private final long deadlineNanos;
        private ServletInputStream delegate;
        private long count;

        LimitedInputStream(HttpServletRequest request, long limit, Duration readTimeout) {
            this.request = request;
            this.limit = limit;
            this.deadlineNanos = System.nanoTime() + readTimeout.toNanos();
        }

        // Só abre o stream original quando alguém lê o corpo, para não
        // impedir um getReader() mais à frente.
        private ServletInputStream delegate() throws IOException {
            if (delegate == null) {
                delegate = request.getInputStream();
            }
            return delegate;
        }

        @Override
        public int read() throws IOException {
            checkDeadline();
            int b = delegate().read();
            if (b != -1) {
                count(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (length == 0) {
                return 0;
            }
            checkDeadline();
            // Nunca lê mais do que o necessário para detetar o excesso.
            int allowed = (int) Math.min(length, limit - count + 1);
            int n = delegate().read(buffer, offset, Math.max(allowed, 1));
            if (n > 0) {
                count(n);
            }
            return n;
        }

        private void count(int n) throws RequestBodyException {
            count += n;
            if (count > limit) {
                throw new RequestBodyException(HttpStatus.CONTENT_TOO_LARGE, tooLargeMessage(limit));
            }
        }

        private void checkDeadline() throws RequestBodyException {
            if (System.nanoTime() - deadlineNanos > 0) {
                throw new RequestBodyException(HttpStatus.REQUEST_TIMEOUT,
                        "O pedido demorou demasiado a chegar. Tenta de novo.");
            }
        }

        @Override
        public boolean isFinished() {
            try {
                return delegate().isFinished();
            } catch (IOException e) {
                return true;
            }
        }

        @Override
        public boolean isReady() {
            try {
                return delegate().isReady();
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public void setReadListener(ReadListener listener) {
            try {
                delegate().setReadListener(listener);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
