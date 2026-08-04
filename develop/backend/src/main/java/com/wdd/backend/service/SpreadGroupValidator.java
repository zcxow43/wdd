package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.CurrencyPairNotFoundException;
import com.wdd.backend.exception.DuplicateSpreadGroupMemberException;
import com.wdd.backend.exception.InvalidSpreadException;
import com.wdd.backend.exception.InvalidSpreadGroupMemberException;
import com.wdd.backend.exception.SpreadGroupNameExistsException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.CurrencyPairMapper;
import com.wdd.backend.mapper.SpreadGroupMapper;
import com.wdd.backend.model.Brand;
import com.wdd.backend.model.CurrencyPair;

/**
 * Shared brand-existence, name-uniqueness-within-brand, non-negative-spread, and
 * currencyPairIds no-duplicates/existence/brand-match validation for the two spread concepts
 * (specs/backend/spread.md) — extracted so it can be reused, verbatim, by both
 * {@link SpreadDefaultAuditHandler} and {@link SpreadGroupAuditHandler}, mirroring
 * {@link CurrencyPairValidator}'s role for {@code currency-pair}.
 */
@Component
public class SpreadGroupValidator {

    private static final int NAME_MAX_LENGTH = 100;

    private final BrandMapper brandMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final SpreadGroupMapper spreadGroupMapper;

    public SpreadGroupValidator(BrandMapper brandMapper, CurrencyPairMapper currencyPairMapper,
            SpreadGroupMapper spreadGroupMapper) {
        this.brandMapper = brandMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.spreadGroupMapper = spreadGroupMapper;
    }

    public Brand requireBrandExists(Long brandId) {
        return brandMapper.findById(brandId).orElseThrow(() -> new BrandNotFoundException(brandId));
    }

    /**
     * Re-checks name presence/length from a deserialized audit snapshot, since a partial-update
     * DTO field cannot carry {@code @NotBlank} (null means "unchanged", not "invalid").
     */
    public void requireNameValid(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidSpreadException("name is required and must not be blank");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new InvalidSpreadException("name must be at most " + NAME_MAX_LENGTH + " characters");
        }
    }

    /**
     * Uniqueness check for (brandId, name) among *live* groups, optionally excluding a given row
     * id — pass {@code excludeId = null} on create, or the row's own id on update.
     */
    public void requireNameUniqueWithinBrand(Long brandId, String name, Long excludeId) {
        spreadGroupMapper.findByBrandAndName(brandId, name, excludeId)
                .ifPresent(existing -> {
                    throw new SpreadGroupNameExistsException(brandId, name);
                });
    }

    public void requireSpreadNonNegative(BigDecimal depositSpread, BigDecimal withdrawSpread) {
        if (depositSpread == null || depositSpread.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSpreadException("depositSpread is required and must be >= 0");
        }
        if (withdrawSpread == null || withdrawSpread.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSpreadException("withdrawSpread is required and must be >= 0");
        }
    }

    /**
     * No duplicate ids within the array; each id must reference an existing currency pair
     * belonging to {@code brandId}. Does NOT check whether a pair already belongs to a
     * *different* group — that is not rejected (specs/backend/spread.md): approving the request
     * moves it.
     */
    public void requireValidMembers(Long brandId, List<Long> currencyPairIds) {
        if (currencyPairIds == null || currencyPairIds.isEmpty()) {
            return;
        }

        Set<Long> seen = new HashSet<>();
        for (Long currencyPairId : currencyPairIds) {
            if (!seen.add(currencyPairId)) {
                throw new DuplicateSpreadGroupMemberException(currencyPairId);
            }
        }

        for (Long currencyPairId : currencyPairIds) {
            CurrencyPair pair = currencyPairMapper.findById(currencyPairId)
                    .orElseThrow(() -> new CurrencyPairNotFoundException(currencyPairId));
            if (!pair.getBrandId().equals(brandId)) {
                throw new InvalidSpreadGroupMemberException(currencyPairId, brandId);
            }
        }
    }
}
