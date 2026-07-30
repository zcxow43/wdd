package pl.piomin.services.backend.exception;

/**
 * Thrown for spread field validation failures that are not covered by a more
 * specific exception - e.g. a negative deposit/withdraw spread re-validated
 * from an audit snapshot (bypassing bean validation), or a blank/too-long
 * spread group name. Mirrors {@code InvalidCurrencyPairException}'s role for
 * {@code CURRENCY_PAIR}.
 */
public class InvalidSpreadException extends RuntimeException {

    public InvalidSpreadException(String message) {
        super(message);
    }
}
