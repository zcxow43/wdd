package com.wdd.backend.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.wdd.backend.dto.AuditPendingResponse;
import com.wdd.backend.dto.Brand;
import com.wdd.backend.dto.BrandSpread;
import com.wdd.backend.dto.BrandSpreadResponse;
import com.wdd.backend.dto.BrandSpreadUpdateRequest;
import com.wdd.backend.exception.BrandNotFoundException;
import com.wdd.backend.exception.InvalidRequestException;
import com.wdd.backend.mapper.BrandMapper;
import com.wdd.backend.mapper.BrandSpreadMapper;

/**
 * Reads/updates a brand's default spread (預設點差) — the fallback tier
 * used by any of its currency pairs that belong to no spread group.
 *
 * <p>{@link #update} validates exactly as before (immediate {@code 400}/
 * {@code 404}/{@code 409}) but, instead of writing, submits an audited
 * change via {@link AuditService} and returns the resulting pending
 * request. The real write (including the create-if-missing zero row) is
 * performed only on approval, by {@link BrandSpreadAuditHandler}. Reads
 * (including {@link #findByBrandId}'s own lazy zero-row creation) are
 * unaffected and untouched by this.
 */
@Service
public class BrandSpreadService {

    private static final String ENTITY_TYPE = "BRAND_SPREAD";
    private static final String ACTION_UPDATE = "UPDATE";

    private final BrandSpreadMapper brandSpreadMapper;
    private final BrandMapper brandMapper;
    private final AuditService auditService;

    public BrandSpreadService(BrandSpreadMapper brandSpreadMapper, BrandMapper brandMapper,
            AuditService auditService) {
        this.brandSpreadMapper = brandSpreadMapper;
        this.brandMapper = brandMapper;
        this.auditService = auditService;
    }

    public List<BrandSpreadResponse> findAll(Long brandId) {
        return brandSpreadMapper.findAll(brandId).stream()
                .map(BrandSpreadService::toResponse)
                .toList();
    }

    @Transactional
    public BrandSpreadResponse findByBrandId(Long brandId) {
        ensureBrandExists(brandId);
        BrandSpread spread = brandSpreadMapper.findByBrandId(brandId);
        if (spread == null) {
            brandSpreadMapper.insertZero(brandId);
            spread = brandSpreadMapper.findByBrandId(brandId);
        }
        return toResponse(spread);
    }

    public AuditPendingResponse update(Long brandId, BrandSpreadUpdateRequest request, String actor) {
        if (request == null) {
            throw new InvalidRequestException("request body is required");
        }
        Brand brand = ensureBrandExists(brandId);

        BigDecimal depositSpread = SpreadValidator.validate(request.getDepositSpread(), "depositSpread");
        BigDecimal withdrawalSpread = SpreadValidator.validate(request.getWithdrawalSpread(), "withdrawalSpread");

        BrandSpread existing = brandSpreadMapper.findByBrandId(brandId);
        BigDecimal currentDeposit = existing != null ? existing.getDepositSpread() : BigDecimal.ZERO;
        BigDecimal currentWithdrawal = existing != null ? existing.getWithdrawalSpread() : BigDecimal.ZERO;

        Map<String, Object> beforeData = new LinkedHashMap<>();
        beforeData.put("depositSpread", currentDeposit);
        beforeData.put("withdrawalSpread", currentWithdrawal);

        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("depositSpread", depositSpread);
        afterData.put("withdrawalSpread", withdrawalSpread);

        String summary = String.format("%s 預設點差調整為入金 %s／出金 %s", brand.getCode(), depositSpread,
                withdrawalSpread);

        var submitted = auditService.submit(ENTITY_TYPE, ACTION_UPDATE, brandId, brandId, summary, beforeData,
                afterData, actor);
        return AuditPendingResponse.from(submitted);
    }

    private Brand ensureBrandExists(Long brandId) {
        Brand brand = brandId != null ? brandMapper.findById(brandId) : null;
        if (brand == null) {
            throw new BrandNotFoundException(brandId);
        }
        return brand;
    }

    private static BrandSpreadResponse toResponse(BrandSpread spread) {
        return new BrandSpreadResponse(
                spread.getBrandId(),
                spread.getBrandCode(),
                spread.getDepositSpread(),
                spread.getWithdrawalSpread(),
                spread.getCreatedAt(),
                spread.getUpdatedAt());
    }
}
