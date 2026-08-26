package com.wdd.backend.service;

import java.math.BigDecimal;

import com.wdd.backend.exception.InvalidRequestException;

/**
 * Shared spread value validation used by both tiers ({@link BrandSpreadService}
 * and {@link SpreadGroupService}): non-null, between {@code 0} and {@code 100}
 * inclusive, at most 8 decimal places (computed with
 * {@code stripTrailingZeros().scale()}, floored at 0, matching the
 * rate-precision check in {@link CurrencyPairService}).
 */
final class SpreadValidator {

    private static final int MAX_SCALE = 8;
    private static final BigDecimal MAX_VALUE = BigDecimal.valueOf(100);

    private SpreadValidator() {
    }

    static BigDecimal validate(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException(fieldName + " must be >= 0");
        }
        if (value.compareTo(MAX_VALUE) > 0) {
            throw new InvalidRequestException(fieldName + " must be <= 100");
        }
        int scale = Math.max(value.stripTrailingZeros().scale(), 0);
        if (scale > MAX_SCALE) {
            throw new InvalidRequestException(fieldName + " must not exceed " + MAX_SCALE + " decimal places");
        }
        return value;
    }
}
