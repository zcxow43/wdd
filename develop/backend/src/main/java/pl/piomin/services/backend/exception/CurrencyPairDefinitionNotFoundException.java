package pl.piomin.services.backend.exception;

public class CurrencyPairDefinitionNotFoundException extends RuntimeException {

    private final Long id;

    public CurrencyPairDefinitionNotFoundException(Long id) {
        super("Currency pair definition not found: " + id);
        this.id = id;
    }

    public Long getId() {
        return id;
    }
}
