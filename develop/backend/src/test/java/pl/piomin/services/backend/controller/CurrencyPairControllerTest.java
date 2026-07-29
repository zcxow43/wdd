package pl.piomin.services.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

@SpringBootTest
@AutoConfigureMockMvc
class CurrencyPairControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrencyPairMapper currencyPairMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CurrencyMapper currencyMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long pugId;
    private Long starId;
    private Long usdId;
    private Long twdId;
    private Long pairId;

    @BeforeEach
    void setUp() {
        for (Long id : currencyPairMapper.findAllIds()) {
            currencyPairMapper.deleteById(id);
        }
        for (Currency currency : currencyMapper.findAll(null)) {
            currencyMapper.deleteById(currency.getId());
        }
        for (Brand brand : brandMapper.findAll(null)) {
            brandMapper.deleteById(brand.getId());
        }

        pugId = insertBrand("PUG");
        starId = insertBrand("STAR");
        usdId = insertCurrency("USD", 2);
        twdId = insertCurrency("TWD", 0);

        CurrencyPair pair = new CurrencyPair();
        pair.setBrandId(pugId);
        pair.setBaseCurrencyId(usdId);
        pair.setQuoteCurrencyId(twdId);
        pair.setRate(new BigDecimal("32.5"));
        pair.setRateType("MANUAL");
        pair.setActive(true);
        currencyPairMapper.insert(pair);
        pairId = pair.getId();
    }

    private Long insertBrand(String code) {
        Brand brand = new Brand();
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        brandMapper.insert(brand);
        return brand.getId();
    }

    private Long insertCurrency(String code, int decimalPlaces) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(decimalPlaces);
        currency.setActive(true);
        currencyMapper.insert(currency);
        return currency.getId();
    }

    @Test
    void list_returnsAllPairs_withCodesPopulated() throws Exception {
        mockMvc.perform(get("/api/currency-pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].brandCode").value("PUG"))
                .andExpect(jsonPath("$[0].baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$[0].quoteCurrencyCode").value("TWD"));
    }

    @Test
    void list_filtersByBrandId() throws Exception {
        mockMvc.perform(get("/api/currency-pairs").param("brandId", String.valueOf(starId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/currency-pairs").param("brandId", String.valueOf(pugId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void list_filtersByActive() throws Exception {
        mockMvc.perform(get("/api/currency-pairs").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/currency-pairs").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void getById_returnsPair_whenFound() throws Exception {
        mockMvc.perform(get("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(32.5))
                .andExpect(jsonPath("$.rateType").value("MANUAL"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/currency-pairs/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void create_returns201_withCreatedPair() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 33.1);
            put("rateType", "AUTO");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.brandCode").value("STAR"))
                .andExpect(jsonPath("$.rateType").value("AUTO"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void create_returns400_whenBaseEqualsQuote() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", usdId);
            put("rate", 1);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Base and quote currency must differ"));
    }

    @Test
    void create_returns404_whenBrandMissing() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", 999999);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 32.5);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }

    @Test
    void create_returns404_whenCurrencyMissing() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", 999999);
            put("quoteCurrencyId", twdId);
            put("rate", 32.5);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"));
    }

    @Test
    void create_returns409_whenDuplicatePairForSameBrand() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", pugId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 40);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Currency pair already exists for this brand"))
                .andExpect(jsonPath("$.brandId").value(pugId))
                .andExpect(jsonPath("$.baseCurrencyId").value(usdId))
                .andExpect(jsonPath("$.quoteCurrencyId").value(twdId));
    }

    @Test
    void create_succeeds_whenSameBaseQuoteUnderDifferentBrand() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 33.0);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brandCode").value("STAR"));
    }

    @Test
    void create_returns400_whenRequiredFieldsMissing() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.brandId").exists())
                .andExpect(jsonPath("$.details.baseCurrencyId").exists())
                .andExpect(jsonPath("$.details.quoteCurrencyId").exists())
                .andExpect(jsonPath("$.details.rateType").exists());
    }

    // Rate/rateType rule integration tests (delta)

    @Test
    void create_returns400_whenRateTypeManualWithoutRate() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rate is required and must be greater than 0 when rateType is MANUAL"));
    }

    @Test
    void create_returns400_whenRateTypeManualWithRateZero() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 0);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.rate").value("rate must be greater than 0"));
    }

    @Test
    void create_returns400_whenRateTypeManualWithRateNegative() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", -1.5);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.rate").value("rate must be greater than 0"));
    }

    @Test
    void create_returns201WithRateNull_whenRateTypeAutoWithRateSupplied() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 100.0);
            put("rateType", "AUTO");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.rateType").value("AUTO"));
    }

    @Test
    void create_returns201WithRateNull_whenRateTypeAutoWithoutRate() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rateType", "AUTO");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.rateType").value("AUTO"));
    }

    @Test
    void update_clearsRate_whenSwitchingManualToAuto() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rateType", "AUTO");
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.rateType").value("AUTO"));
    }

    @Test
    void update_clearsRate_whenSwitchingToAutoEvenIfRateSupplied() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rateType", "AUTO");
            put("rate", 999.0);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.rateType").value("AUTO"));
    }

    @Test
    void update_returns400_whenSwitchingAutoToManualWithoutRate() throws Exception {
        // First create an AUTO pair with rate null
        CurrencyPair autoPair = new CurrencyPair();
        autoPair.setBrandId(starId);
        autoPair.setBaseCurrencyId(usdId);
        autoPair.setQuoteCurrencyId(twdId);
        autoPair.setRate(null);
        autoPair.setRateType("AUTO");
        autoPair.setActive(true);
        currencyPairMapper.insert(autoPair);

        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", autoPair.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("rate is required and must be greater than 0 when rateType is MANUAL"));
    }

    @Test
    void update_succeeds_whenSwitchingToManualWithValidRate() throws Exception {
        // First create an AUTO pair with rate null
        CurrencyPair autoPair = new CurrencyPair();
        autoPair.setBrandId(starId);
        autoPair.setBaseCurrencyId(usdId);
        autoPair.setQuoteCurrencyId(twdId);
        autoPair.setRate(null);
        autoPair.setRateType("AUTO");
        autoPair.setActive(true);
        currencyPairMapper.insert(autoPair);

        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rateType", "MANUAL");
            put("rate", 42.0);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", autoPair.getId()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(42.0))
                .andExpect(jsonPath("$.rateType").value("MANUAL"));
    }

    @Test
    void getById_serializesRateAsNull_whenRateTypeAuto() throws Exception {
        // Create an AUTO pair
        CurrencyPair autoPair = new CurrencyPair();
        autoPair.setBrandId(starId);
        autoPair.setBaseCurrencyId(usdId);
        autoPair.setQuoteCurrencyId(twdId);
        autoPair.setRate(null);
        autoPair.setRateType("AUTO");
        autoPair.setActive(true);
        currencyPairMapper.insert(autoPair);

        mockMvc.perform(get("/api/currency-pairs/{id}", autoPair.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").doesNotExist())
                .andExpect(jsonPath("$.rateType").value("AUTO"));
    }

    @Test
    void update_returns200_withUpdatedPair() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 40.0);
            put("active", false);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(40.0))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 40.0);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", 999999).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"));
    }

    @Test
    void update_returns400_whenRateNotPositive() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", -1);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.rate").exists());
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"));
    }
}
