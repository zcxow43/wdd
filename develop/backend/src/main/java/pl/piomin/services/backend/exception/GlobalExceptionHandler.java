package pl.piomin.services.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pl.piomin.services.backend.audit.AuditRequestAlreadyReviewedException;
import pl.piomin.services.backend.audit.AuditRequestNotFoundException;
import pl.piomin.services.backend.audit.DuplicatePendingAuditRequestException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CurrencyNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(CurrencyNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(BrandNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(BrandNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Brand not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyCodeExistsException.class)
    public ResponseEntity<Map<String, Object>> handleCodeExists(CurrencyCodeExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency code already exists");
        body.put("code", ex.getCode());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CurrencyInUseException.class)
    public ResponseEntity<Map<String, Object>> handleInUse(CurrencyInUseException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency is referenced by one or more currency pairs and cannot be deleted");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(CurrencyPairNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(CurrencyPairNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(CurrencyPairExistsException.class)
    public ResponseEntity<Map<String, Object>> handleExists(CurrencyPairExistsException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Currency pair already exists for this brand");
        body.put("brandId", ex.getBrandId());
        body.put("baseCurrencyId", ex.getBaseCurrencyId());
        body.put("quoteCurrencyId", ex.getQuoteCurrencyId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(InvalidCurrencyPairException.class)
    public ResponseEntity<Map<String, Object>> handleInvalid(InvalidCurrencyPairException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(DuplicatePendingCurrencyPairCreateException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePendingCreate(
            DuplicatePendingCurrencyPairCreateException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(AuditRequestNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(AuditRequestNotFoundException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Audit request not found");
        body.put("id", ex.getId());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(AuditRequestAlreadyReviewedException.class)
    public ResponseEntity<Map<String, Object>> handleAlreadyReviewed(AuditRequestAlreadyReviewedException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Audit request has already been reviewed");
        body.put("id", ex.getId());
        body.put("status", ex.getStatus());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(DuplicatePendingAuditRequestException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicatePending(DuplicatePendingAuditRequestException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "A pending audit request already exists for this entity");
        body.put("entityType", ex.getEntityType());
        body.put("entityId", ex.getEntityId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fieldError ->
                details.put(fieldError.getField(), fieldError.getDefaultMessage()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Validation failed");
        body.put("details", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
