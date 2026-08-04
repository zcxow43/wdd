package com.wdd.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wdd.backend.audit.AuditRequestAlreadyReviewedException;
import com.wdd.backend.audit.AuditRequestNotFoundException;
import com.wdd.backend.audit.DuplicatePendingAuditRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CurrencyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyNotFound(CurrencyNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyCodeExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyCodeExists(CurrencyCodeExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency code already exists");
        body.put("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBrandNotFound(BrandNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Brand not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyPairNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyPairNotFound(CurrencyPairNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyPairExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyPairExists(CurrencyPairExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair already exists for this brand/base/quote combination");
        body.put("brandId", ex.getBrandId());
        body.put("baseCurrencyId", ex.getBaseCurrencyId());
        body.put("quoteCurrencyId", ex.getQuoteCurrencyId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CurrencyInUseException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyInUse(CurrencyInUseException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency is referenced by one or more currency pairs and cannot be deleted");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCurrencyPairException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCurrencyPair(InvalidCurrencyPairException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(CurrencyPairDefinitionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyPairDefinitionNotFound(
            CurrencyPairDefinitionNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair definition not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyPairDefinitionExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyPairDefinitionExists(
            CurrencyPairDefinitionExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "A currency pair definition already exists for this pair or its reverse direction");
        body.put("baseCurrencyId", ex.getBaseCurrencyId());
        body.put("quoteCurrencyId", ex.getQuoteCurrencyId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CurrencyPairDefinitionInUseException.class)
    public ResponseEntity<Map<String, Object>> handleCurrencyPairDefinitionInUse(
            CurrencyPairDefinitionInUseException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error",
                "One or more brands still have this currency pair active; disable it for every brand before deleting");
        body.put("baseCurrencyId", ex.getBaseCurrencyId());
        body.put("quoteCurrencyId", ex.getQuoteCurrencyId());
        body.put("activeBrandCodes", ex.getActiveBrandCodes());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(SpreadDefaultNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSpreadDefaultNotFound(SpreadDefaultNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Spread default not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(SpreadGroupNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSpreadGroupNotFound(SpreadGroupNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Spread group not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(SpreadGroupNameExistsException.class)
    public ResponseEntity<Map<String, Object>> handleSpreadGroupNameExists(SpreadGroupNameExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Spread group name already exists for this brand");
        body.put("brandId", ex.getBrandId());
        body.put("name", ex.getName());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicatePendingSpreadGroupCreateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePendingSpreadGroupCreate(
            DuplicatePendingSpreadGroupCreateException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "A pending create request already exists for this brand/name combination");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidSpreadGroupMemberException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSpreadGroupMember(InvalidSpreadGroupMemberException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair does not belong to the group's brand");
        body.put("currencyPairId", ex.getCurrencyPairId());
        body.put("brandId", ex.getBrandId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicateSpreadGroupMemberException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateSpreadGroupMember(DuplicateSpreadGroupMemberException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Duplicate currency pair id in currencyPairIds");
        body.put("currencyPairId", ex.getCurrencyPairId());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(InvalidSpreadException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidSpread(InvalidSpreadException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AuditRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAuditRequestNotFound(AuditRequestNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Audit request not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AuditRequestAlreadyReviewedException.class)
    public ResponseEntity<Map<String, Object>> handleAuditRequestAlreadyReviewed(AuditRequestAlreadyReviewedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Audit request has already been reviewed");
        body.put("id", ex.getId());
        body.put("status", ex.getStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicatePendingAuditRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePendingAuditRequest(DuplicatePendingAuditRequestException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "A pending audit request already exists for this entity");
        body.put("entityType", ex.getEntityType());
        body.put("entityId", ex.getEntityId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage()));
        body.put("fields", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
