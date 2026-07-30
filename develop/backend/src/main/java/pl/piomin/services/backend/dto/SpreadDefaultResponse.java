package pl.piomin.services.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import pl.piomin.services.backend.model.SpreadDefault;

public class SpreadDefaultResponse {

    private Long id;
    private Long brandId;
    private String brandCode;
    private BigDecimal depositSpread;
    private BigDecimal withdrawSpread;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SpreadDefaultResponse from(SpreadDefault spreadDefault) {
        SpreadDefaultResponse response = new SpreadDefaultResponse();
        response.id = spreadDefault.getId();
        response.brandId = spreadDefault.getBrandId();
        response.brandCode = spreadDefault.getBrandCode();
        response.depositSpread = spreadDefault.getDepositSpread();
        response.withdrawSpread = spreadDefault.getWithdrawSpread();
        response.createdAt = spreadDefault.getCreatedAt();
        response.updatedAt = spreadDefault.getUpdatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public BigDecimal getDepositSpread() {
        return depositSpread;
    }

    public BigDecimal getWithdrawSpread() {
        return withdrawSpread;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
