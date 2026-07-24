package pl.piomin.services.backend.exception;

public class CurrencyNotFoundException extends RuntimeException {

    private final Long id;

    public CurrencyNotFoundException(Long id) {
        super("Currency not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
