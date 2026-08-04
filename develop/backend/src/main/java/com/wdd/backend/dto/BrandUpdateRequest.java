package com.wdd.backend.dto;

import jakarta.validation.constraints.NotNull;

public class BrandUpdateRequest {

    @NotNull(message = "active is required")
    private Boolean active;

    public BrandUpdateRequest() {
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
