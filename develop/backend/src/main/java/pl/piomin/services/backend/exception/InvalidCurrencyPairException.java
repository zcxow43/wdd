package pl.piomin.services.backend.exception;

/**
 * Thrown for currency pair business-rule validation failures that cannot be
 * expressed as a simple per-field Bean Validation constraint (e.g. base and
 * quote currency must differ).
 */
public class InvalidCurrencyPairException extends RuntimeException {

    public InvalidCurrencyPairException(String message) {
        super(message);
    }
}
