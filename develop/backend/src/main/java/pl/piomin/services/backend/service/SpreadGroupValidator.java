package pl.piomin.services.backend.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import pl.piomin.services.backend.exception.BrandNotFoundException;
import pl.piomin.services.backend.exception.CurrencyPairNotFoundException;
import pl.piomin.services.backend.exception.DuplicateSpreadGroupMemberException;
import pl.piomin.services.backend.exception.InvalidSpreadException;
import pl.piomin.services.backend.exception.InvalidSpreadGroupMemberException;
import pl.piomin.services.backend.exception.SpreadGroupNameExistsException;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.mapper.SpreadGroupMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.CurrencyPair;
import pl.piomin.services.backend.model.SpreadGroup;

/**
 * Shared spread business-rule validation: brand existence, name non-blank/
 * uniqueness-within-brand, non-negative spread values, and currencyPairIds
 * no-duplicates/existence/brand-match. Extracted so the exact same rules can
 * be reused by both {@link SpreadDefaultService}/{@link SpreadDefaultAuditHandler}
 * (spread non-negative check only) and {@link SpreadGroupService}/
 * {@link SpreadGroupAuditHandler}, mirroring {@code CurrencyPairValidator}.
 */
@Component
public class SpreadGroupValidator {

    private final BrandMapper brandMapper;
    private final CurrencyPairMapper currencyPairMapper;
    private final SpreadGroupMapper spreadGroupMapper;

    public SpreadGroupValidator(BrandMapper brandMapper, CurrencyPairMapper currencyPairMapper,
                                 SpreadGroupMapper spreadGroupMapper) {
        this.brandMapper = brandMapper;
        this.currencyPairMapper = currencyPairMapper;
        this.spreadGroupMapper = spreadGroupMapper;
    }

    public Brand validateBrandExists(Long brandId) {
        Brand brand = brandMapper.findById(brandId);
        if (brand == null) {
            throw new BrandNotFoundException(brandId);
        }
        return brand;
    }

    public void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidSpreadException("name must not be blank");
        }
        if (name.length() > 100) {
            throw new InvalidSpreadException("name must be at most 100 characters");
        }
    }

    public void validateSpreadNonNegative(BigDecimal depositSpread, BigDecimal withdrawSpread) {
        if (depositSpread == null || depositSpread.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSpreadException("depositSpread is required and must be >= 0");
        }
        if (withdrawSpread == null || withdrawSpread.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSpreadException("withdrawSpread is required and must be >= 0");
        }
    }

    public void validateUniqueName(Long brandId, String name, Long excludeId) {
        SpreadGroup other = spreadGroupMapper.findByBrandAndName(brandId, name);
        if (other != null && !other.getId().equals(excludeId)) {
            throw new SpreadGroupNameExistsException(brandId, name);
        }
    }

    /**
     * Validates a proposed member list: no duplicates, each id must exist, and
     * each pair's brand must match {@code brandId}. Returns the resolved
     * (enriched, with codes) {@link CurrencyPair} rows in the same order as
     * {@code currencyPairIds}, for building the snapshot's {@code members} enrichment.
     */
    public List<CurrencyPair> validateMembers(Long brandId, List<Long> currencyPairIds) {
        Set<Long> seen = new HashSet<>();
        for (Long pairId : currencyPairIds) {
            if (!seen.add(pairId)) {
                throw new DuplicateSpreadGroupMemberException(pairId);
            }
        }

        List<CurrencyPair> pairs = new ArrayList<>();
        for (Long pairId : currencyPairIds) {
            CurrencyPair pair = currencyPairMapper.findById(pairId);
            if (pair == null) {
                throw new CurrencyPairNotFoundException(pairId);
            }
            if (!pair.getBrandId().equals(brandId)) {
                throw new InvalidSpreadGroupMemberException(pairId, brandId);
            }
            pairs.add(pair);
        }
        return pairs;
    }
}
