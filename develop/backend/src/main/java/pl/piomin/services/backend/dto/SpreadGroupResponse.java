package pl.piomin.services.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import pl.piomin.services.backend.model.SpreadGroup;
import pl.piomin.services.backend.model.SpreadGroupMember;

public class SpreadGroupResponse {

    private Long id;
    private Long brandId;
    private String brandCode;
    private String name;
    private BigDecimal depositSpread;
    private BigDecimal withdrawSpread;
    private List<SpreadGroupMemberResponse> members;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static SpreadGroupResponse from(SpreadGroup group, List<SpreadGroupMember> members) {
        SpreadGroupResponse response = new SpreadGroupResponse();
        response.id = group.getId();
        response.brandId = group.getBrandId();
        response.brandCode = group.getBrandCode();
        response.name = group.getName();
        response.depositSpread = group.getDepositSpread();
        response.withdrawSpread = group.getWithdrawSpread();
        response.members = members.stream().map(SpreadGroupMemberResponse::from).toList();
        response.createdAt = group.getCreatedAt();
        response.updatedAt = group.getUpdatedAt();
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

    public String getName() {
        return name;
    }

    public BigDecimal getDepositSpread() {
        return depositSpread;
    }

    public BigDecimal getWithdrawSpread() {
        return withdrawSpread;
    }

    public List<SpreadGroupMemberResponse> getMembers() {
        return members;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
