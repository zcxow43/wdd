package com.wdd.backend.exception;

/**
 * Small dedicated 400 for spread-field validation rules that must be re-checked from a
 * deserialized audit snapshot ({@code Map<String,Object>}, bypassing Bean Validation entirely)
 * rather than from a DTO — shared by both SPREAD_DEFAULT and SPREAD_GROUP: negative
 * deposit/withdraw spread, and blank/over-100-char spread group names on a partial PUT (where the
 * DTO field is optional and so cannot carry {@code @NotBlank}). Mirrors
 * {@link InvalidCurrencyPairException}'s role for CURRENCY_PAIR.
 */
public class InvalidSpreadException extends RuntimeException {

    public InvalidSpreadException(String message) {
        super(message);
    }
}
