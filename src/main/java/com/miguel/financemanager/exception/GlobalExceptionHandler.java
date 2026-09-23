package com.miguel.financemanager.exception;

import com.miguel.financemanager.config.RequestBodyLimitFilter.RequestBodyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    // 404 também para recursos de outro utilizador (ver ResourceNotFoundException).
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(ImportConflictException.class)
    public ResponseEntity<Map<String, String>> handleImportConflict(ImportConflictException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(DuplicateResolutionException.class)
    public ResponseEntity<Map<String, String>> handleDuplicateResolution(DuplicateResolutionException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }

    // Qualquer outra violação de integridade (ex.: índice único) é um conflito
    // com dados existentes, não um erro interno.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Os dados entram em conflito com registos existentes. Tenta de novo."));
    }

    @ExceptionHandler(RegistrationDisabledException.class)
    public ResponseEntity<Map<String, String>> handleRegistrationDisabled(RegistrationDisabledException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
    }

    // Corpo ilegível. Se a causa for o RequestBodyLimitFilter (corpo sem
    // Content-Length que passou do limite, ou que demorou demasiado), responde
    // 413/408 com a mensagem dele; senão, 400.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleNotReadable(HttpMessageNotReadableException e) {
        RequestBodyException bodyError = RequestBodyException.findIn(e);
        if (bodyError != null) {
            return ResponseEntity.status(bodyError.status()).body(Map.of("error", bodyError.getMessage()));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Pedido inválido."));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Map<String, String>> handlePayloadTooLarge(PayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE).body(Map.of("error", e.getMessage()));
    }

    // Upload acima de spring.servlet.multipart.max-file-size/max-request-size.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.CONTENT_TOO_LARGE)
                .body(Map.of("error", "O ficheiro é demasiado grande (máximo 1 MB)."));
    }

    // Outros multipart inválidos (ex.: partes a mais): 400 em vez de 500.
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, String>> handleMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Pedido inválido."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, String>> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<Map<String, String>> handleTooManyAttempts(TooManyAttemptsException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(Map.of("error", e.getMessage()));
    }
}
