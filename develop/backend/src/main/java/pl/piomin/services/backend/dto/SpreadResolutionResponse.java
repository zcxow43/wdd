package pl.piomin.services.backend.dto;

import java.math.BigDecimal;

/**
 * Response DTO for {@code GET /api/spread-groups/resolve/{currencyPairId}}.
 * Always built from live, already-approved data - never affected by a
 * PENDING audit request.
 */
public class SpreadResolutionResponse {

    private final Long currencyPairId;
    private final Long brandId;
    private final String source;
    private final Long spreadGroupId;
    private final String spreadGroupName;
    private final BigDecimal depositSpread;
    private final BigDecimal withdrawSpread;

    public SpreadResolutionResponse(Long currencyPairId, Long brandId, String source, Long spreadGroupId,
                                     String spreadGroupName, BigDecimal depositSpread, BigDecimal withdrawSpread) {
        this.currencyPairId = currencyPairId;
        this.brandId = brandId;
        this.source = source;
        this.spreadGroupId = spreadGroupId;
        this.spreadGroupName = spreadGroupName;
        this.depositSpread = depositSpread;
        this.withdrawSpread = withdrawSpread;
    }

    public Long getCurrencyPairId() {
        return currencyPairId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getSource() {
        return source;
    }

    public Long getSpreadGroupId() {
        return spreadGroupId;
    }

    public String getSpreadGroupName() {
        return spreadGroupName;
    }

    public BigDecimal getDepositSpread() {
        return depositSpread;
    }

    public BigDecimal getWithdrawSpread() {
        return withdrawSpread;
    }
}
