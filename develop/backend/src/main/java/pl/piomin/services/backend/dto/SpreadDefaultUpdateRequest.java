package pl.piomin.services.backend.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code PUT /api/spread-defaults/{id}}. Submits a change
 * request through the audit module instead of updating directly.
 */
public class SpreadDefaultUpdateRequest {

    @NotNull(message = "depositSpread is required")
    @DecimalMin(value = "0", message = "depositSpread must be >= 0")
    private BigDecimal depositSpread;

    @NotNull(message = "withdrawSpread is required")
    @DecimalMin(value = "0", message = "withdrawSpread must be >= 0")
    private BigDecimal withdrawSpread;

    /** Optional free-text submitter name, passed through to AuditService.submit. */
    private String requestedBy;

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

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
}
