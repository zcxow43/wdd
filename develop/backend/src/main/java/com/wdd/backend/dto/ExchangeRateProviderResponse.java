package com.wdd.backend.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Shape of the response from {@code GET https://open.er-api.com/v6/latest/{baseCode}}.
 * Only the fields this feature needs are mapped; everything else is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExchangeRateProviderResponse {

    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    private Map<String, BigDecimal> rates;

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getBaseCode() {
        return baseCode;
    }

    public void setBaseCode(String baseCode) {
        this.baseCode = baseCode;
    }

    public Map<String, BigDecimal> getRates() {
        return rates;
    }

    public void setRates(Map<String, BigDecimal> rates) {
        this.rates = rates;
    }
}
