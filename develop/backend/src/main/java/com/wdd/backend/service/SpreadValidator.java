package com.wdd.backend.service;

import java.math.BigDecimal;

import com.wdd.backend.exception.InvalidRequestException;

/**
 * Shared spread value validation used by both tiers ({@link BrandSpreadService}
 * and {@link SpreadGroupService}): non-null, {@code >= 0}, at most 8 decimal
 * places (computed with {@code stripTrailingZeros().scale()}, floored at 0,
 * matching the rate-precision check in {@link CurrencyPairService}).
 */
final class SpreadValidator {

    private static final int MAX_SCALE = 8;

    private SpreadValidator() {
    }

    static BigDecimal validate(BigDecimal value, String fieldName) {
        if (value == null) {
            throw new InvalidRequestException(fieldName + " is required");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException(fieldName + " must be >= 0");
        }
        int scale = Math.max(value.stripTrailingZeros().scale(), 0);
        if (scale > MAX_SCALE) {
            throw new InvalidRequestException(fieldName + " must not exceed " + MAX_SCALE + " decimal places");
        }
        return value;
    }
}
