package com.wdd.backend.exception;

import java.util.List;

/**
 * Thrown when a currency pair definition cannot be deleted because at least
 * one of its brand currency pairs is still active.
 */
public class ActiveCurrencyPairsExistException extends RuntimeException {

    private final List<String> activeBrandCodes;

    public ActiveCurrencyPairsExistException(List<String> activeBrandCodes) {
        super("Active brand currency pairs exist");
        this.activeBrandCodes = activeBrandCodes;
    }

    public List<String> getActiveBrandCodes() {
        return activeBrandCodes;
    }
}
