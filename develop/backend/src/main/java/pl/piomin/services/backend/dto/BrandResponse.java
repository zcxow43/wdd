package pl.piomin.services.backend.dto;

import java.time.LocalDateTime;

import pl.piomin.services.backend.model.Brand;

public class BrandResponse {

    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public BrandResponse() {
    }

    public BrandResponse(Brand brand) {
        this.id = brand.getId();
        this.code = brand.getCode();
        this.name = brand.getName();
        this.active = brand.getActive();
        this.createdAt = brand.getCreatedAt();
        this.updatedAt = brand.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
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
