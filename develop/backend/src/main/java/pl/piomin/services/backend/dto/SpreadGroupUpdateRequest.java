package pl.piomin.services.backend.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for partially updating a spread group. Every field is optional;
 * when present it must satisfy the same constraints as on create. Same
 * partial-update convention as {@code CurrencyPairUpdateRequest}. When
 * {@code currencyPairIds} is omitted entirely, membership is left unchanged.
 */
public class SpreadGroupUpdateRequest {

    @Size(max = 100, message = "name must be at most 100 characters")
    private String name;

    @DecimalMin(value = "0", message = "depositSpread must be >= 0")
    private BigDecimal depositSpread;

    @DecimalMin(value = "0", message = "withdrawSpread must be >= 0")
    private BigDecimal withdrawSpread;

    private List<Long> currencyPairIds;

    /** Optional free-text submitter name, passed through to AuditService.submit. */
    private String requestedBy;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getDepositSpread() {
        return depositSpread;
    }

    public void setDepositSpread(BigDecimal depositSpread) {
        this.depositSpread = depositSpread;
    }

    public BigDecimal getWithdrawSpread() {
        return withdrawSpread;
    }

    public void setWithdrawSpread(BigDecimal withdrawSpread) {
        this.withdrawSpread = withdrawSpread;
    }

    public List<Long> getCurrencyPairIds() {
        return currencyPairIds;
    }

    public void setCurrencyPairIds(List<Long> currencyPairIds) {
        this.currencyPairIds = currencyPairIds;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
