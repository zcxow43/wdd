package com.wdd.backend.dto;

public class BrandUpdateRequest {

    private Boolean active;

    public BrandUpdateRequest() {
    }

    public BrandUpdateRequest(Boolean active) {
        this.active = active;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
