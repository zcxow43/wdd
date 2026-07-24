package pl.piomin.services.backend.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for toggling a brand's active state. No other field is
 * editable via the API.
 */
public class BrandUpdateRequest {

    @NotNull(message = "active is required")
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
