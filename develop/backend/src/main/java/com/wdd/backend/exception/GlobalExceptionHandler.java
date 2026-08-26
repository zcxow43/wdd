package com.wdd.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(BrandNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CurrencyNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyCodeConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(CurrencyCodeConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyPairDefinitionNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CurrencyPairDefinitionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyPairDefinitionConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(CurrencyPairDefinitionConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(ActiveCurrencyPairsExistException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ActiveCurrencyPairsExistException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("activeBrandCodes", ex.getActiveBrandCodes());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CurrencyPairNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(CurrencyPairNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(CurrencyPairConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(CurrencyPairConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<Map<String, String>> handleInvalidRequest(InvalidRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(SpreadGroupNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SpreadGroupNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(SpreadGroupMemberNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(SpreadGroupMemberNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(SpreadGroupNameConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(SpreadGroupNameConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UnknownCurrencyPairIdsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(UnknownCurrencyPairIdsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("currencyPairIds", ex.getCurrencyPairIds());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(CurrencyPairBrandMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRequest(CurrencyPairBrandMismatchException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("currencyPairIds", ex.getCurrencyPairIds());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(SpreadGroupMemberConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(SpreadGroupMemberConflictException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("conflicts", ex.getConflicts());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AuditRequestNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(AuditRequestNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AuditRequestConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflict(AuditRequestConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", ex.getMessage()));
    }

    @ExceptionHandler(AuditApplyFailedException.class)
    public ResponseEntity<Map<String, Object>> handleUnprocessable(AuditApplyFailedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("auditRequestId", ex.getAuditRequestId());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler(ExchangeRateSyncCooldownException.class)
    public ResponseEntity<Map<String, Object>> handleTooManyRequests(ExchangeRateSyncCooldownException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        body.put("retryAfterSeconds", ex.getRetryAfterSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(body);
    }

    @ExceptionHandler(ExchangeRateProviderUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleBadGateway(ExchangeRateProviderUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", ex.getMessage()));
    }
}
