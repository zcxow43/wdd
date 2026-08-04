package com.wdd.backend.exception;

/**
 * Thrown for cross-field currency-pair business rules that cannot be expressed as
 * independent per-field Bean Validation annotations (e.g. base == quote, or the
 * rate/rateType combination rule).
 */
public class InvalidCurrencyPairException extends RuntimeException {

    public InvalidCurrencyPairException(String message) {
        super(message);
    }
}
