package pl.piomin.services.backend.exception;

public class CurrencyInUseException extends RuntimeException {

    private final Long id;

    public CurrencyInUseException(Long id) {
        super("Currency is referenced by one or more currency pairs and cannot be deleted: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
