package pl.piomin.services.backend.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
