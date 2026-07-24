package pl.piomin.services.backend.dto;

import java.time.LocalDateTime;

import pl.piomin.services.backend.model.Brand;

/**
 * Response DTO returned by all brand endpoints.
 */
public class BrandResponse {

    private Long id;
    private String code;
    private String name;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static BrandResponse from(Brand brand) {
        BrandResponse response = new BrandResponse();
        response.id = brand.getId();
        response.code = brand.getCode();
        response.name = brand.getName();
        response.active = brand.getActive();
        response.createdAt = brand.getCreatedAt();
        response.updatedAt = brand.getUpdatedAt();
        return response;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Boolean getActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
