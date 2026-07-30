package pl.piomin.services.backend.exception;

public class SpreadGroupNameExistsException extends RuntimeException {

    private final Long brandId;
    private final String name;

    public SpreadGroupNameExistsException(Long brandId, String name) {
        super("Spread group name already exists for this brand: brandId=" + brandId + ", name=" + name);
        this.brandId = brandId;
        this.name = name;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }
}
