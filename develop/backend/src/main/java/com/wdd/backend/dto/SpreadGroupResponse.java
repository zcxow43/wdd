package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.wdd.backend.model.SpreadGroup;
import com.wdd.backend.model.SpreadGroupMember;

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

    public SpreadGroupResponse() {
    }

    public static SpreadGroupResponse from(SpreadGroup group, List<SpreadGroupMember> members) {
        SpreadGroupResponse response = new SpreadGroupResponse();
        response.setId(group.getId());
        response.setBrandId(group.getBrandId());
        response.setBrandCode(group.getBrandCode());
        response.setName(group.getName());
        response.setDepositSpread(group.getDepositSpread());
        response.setWithdrawSpread(group.getWithdrawSpread());
        response.setMembers(members.stream().map(SpreadGroupMemberResponse::from).collect(Collectors.toList()));
        response.setCreatedAt(group.getCreatedAt());
        response.setUpdatedAt(group.getUpdatedAt());
        return response;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

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

    public List<SpreadGroupMemberResponse> getMembers() {
        return members;
    }

    public void setMembers(List<SpreadGroupMemberResponse> members) {
        this.members = members;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
