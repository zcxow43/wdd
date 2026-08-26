package com.wdd.backend.exception;

/**
 * Thrown when every base currency's call to the external rate provider
 * failed during a sync, so nothing was written.
 */
public class ExchangeRateProviderUnavailableException extends RuntimeException {

    public ExchangeRateProviderUnavailableException() {
        super("Failed to fetch rates from external provider");
    }
}
