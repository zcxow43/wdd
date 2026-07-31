package pl.piomin.services.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import pl.piomin.services.backend.mapper.CurrencyPairDefinitionMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

import java.util.HashMap;

/**
 * Integration tests for {@code /api/currency-pair-definitions}. Unlike
 * {@code CurrencyPairControllerTest}, POST/PUT/DELETE here apply immediately
 * and never go through the audit-approval workflow.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CurrencyPairDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrencyPairDefinitionMapper currencyPairDefinitionMapper;

    @Autowired
    private CurrencyPairMapper currencyPairMapper;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private CurrencyMapper currencyMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long usdId;
    private Long jpyId;
    private Long pugId;
    private Long starId;

    @BeforeEach
    void setUp() {
        for (Long id : currencyPairDefinitionMapper.findAllIds()) {
            currencyPairDefinitionMapper.deleteById(id);
        }
        for (Long id : currencyPairMapper.findAllIds()) {
            currencyPairMapper.deleteById(id);
        }
        for (Currency currency : currencyMapper.findAll()) {
            currencyMapper.deleteById(currency.getId());
        }
        for (Brand brand : brandMapper.findAll(null)) {
            brandMapper.deleteById(brand.getId());
        }

        usdId = insertCurrency("USD");
        jpyId = insertCurrency("JPY");
        pugId = insertBrand("PUG");
        starId = insertBrand("STAR");
    }

    private Long insertBrand(String code) {
        Brand brand = new Brand();
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(true);
        brandMapper.insert(brand);
        return brand.getId();
    }

    private Long insertCurrency(String code) {
        Currency currency = new Currency();
        currency.setCode(code);
        currency.setName(code);
        currency.setDecimalPlaces(2);
        currencyMapper.insert(currency);
        return currency.getId();
    }

    private String createBody(Long base, Long quote, int fwd, int rev) throws Exception {
        return objectMapper.writeValueAsString(new HashMap<>() {{
            put("baseCurrencyId", base);
            put("quoteCurrencyId", quote);
            put("forwardPrecision", fwd);
            put("reversePrecision", rev);
        }});
    }

    @Test
    void create_returns201_andFansOutToAllBrands() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("JPY"))
                .andExpect(jsonPath("$.forwardPrecision").value(2))
                .andExpect(jsonPath("$.reversePrecision").value(5));

        org.assertj.core.api.Assertions.assertThat(currencyPairMapper.findAll(null, null)).hasSize(2);
        CurrencyPair pugPair = currencyPairMapper.findByBrandBaseQuote(pugId, usdId, jpyId);
        org.assertj.core.api.Assertions.assertThat(pugPair).isNotNull();
        org.assertj.core.api.Assertions.assertThat(pugPair.getRateType()).isEqualTo("AUTO");
        org.assertj.core.api.Assertions.assertThat(pugPair.getRate()).isNull();
        org.assertj.core.api.Assertions.assertThat(pugPair.getActive()).isTrue();
    }

    @Test
    void create_leavesExistingBrandRowUntouched_butProvisionsOthers() throws Exception {
        CurrencyPair existing = new CurrencyPair();
        existing.setBrandId(pugId);
        existing.setBaseCurrencyId(usdId);
        existing.setQuoteCurrencyId(jpyId);
        existing.setRate(new java.math.BigDecimal("110.5"));
        existing.setRateType("MANUAL");
        existing.setActive(true);
        currencyPairMapper.insert(existing);

        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        CurrencyPair pugPair = currencyPairMapper.findById(existing.getId());
        org.assertj.core.api.Assertions.assertThat(pugPair.getRateType()).isEqualTo("MANUAL");
        org.assertj.core.api.Assertions.assertThat(pugPair.getRate()).isEqualByComparingTo("110.5");

        CurrencyPair starPair = currencyPairMapper.findByBrandBaseQuote(starId, usdId, jpyId);
        org.assertj.core.api.Assertions.assertThat(starPair).isNotNull();
        org.assertj.core.api.Assertions.assertThat(starPair.getRateType()).isEqualTo("AUTO");
    }

    @Test
    void create_returns409_whenReverseDirectionAlreadyDefined() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(jpyId, usdId, 2, 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("A currency pair definition already exists for this pair or its reverse direction"));

        org.assertj.core.api.Assertions.assertThat(currencyPairDefinitionMapper.findAll(null, null)).hasSize(1);
    }

    @Test
    void create_returns409_whenExactDirectionAlreadyDefined() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 3, 6)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_returns400_whenBaseEqualsQuote() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, usdId, 2, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Base and quote currency must differ"));
    }

    @Test
    void create_returns404_whenCurrencyMissing() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(999999L, jpyId, 2, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"));
    }

    @Test
    void create_returns400_whenPrecisionOutOfRange() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 9, 5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.forwardPrecision").exists());
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/currency-pair-definitions/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair definition not found"));
    }

    @Test
    void list_filtersByBaseAndQuoteCurrencyId() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/currency-pair-definitions").param("baseCurrencyId", String.valueOf(usdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get("/api/currency-pair-definitions").param("baseCurrencyId", String.valueOf(jpyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    void update_updatesPrecision() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("forwardPrecision", 3);
            put("reversePrecision", 6);
        }});

        mockMvc.perform(put("/api/currency-pair-definitions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forwardPrecision").value(3))
                .andExpect(jsonPath("$.reversePrecision").value(6))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"));
    }

    @Test
    void update_returns400_whenPrecisionOutOfRange() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("forwardPrecision", -1);
            put("reversePrecision", 6);
        }});

        mockMvc.perform(put("/api/currency-pair-definitions/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        String updateBody = objectMapper.writeValueAsString(new HashMap<>() {{
            put("forwardPrecision", 3);
            put("reversePrecision", 6);
        }});

        mockMvc.perform(put("/api/currency-pair-definitions/{id}", 999999).contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesDefinition_butLeavesProvisionedCurrencyPairsUntouched() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        // deactivate every provisioned row for every brand first, per the delete guard
        for (CurrencyPair pair : currencyPairMapper.findAll(null, null)) {
            pair.setActive(false);
            currencyPairMapper.update(pair);
        }

        mockMvc.perform(delete("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isNotFound());

        // provisioned currency_pair rows remain, just inactive (deletion never touches them)
        org.assertj.core.api.Assertions.assertThat(currencyPairMapper.findAll(null, null)).hasSize(2);
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/currency-pair-definitions/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns409_withActiveBrandCodes_whenAnyBrandStillActive() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        // Both PUG and STAR were provisioned active=true by the fan-out; deactivate
        // STAR only, leaving PUG active, so the guard must still block deletion.
        CurrencyPair starPair = currencyPairMapper.findByBrandBaseQuote(starId, usdId, jpyId);
        starPair.setActive(false);
        currencyPairMapper.update(starPair);

        mockMvc.perform(delete("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "One or more brands still have this currency pair active; "
                                + "disable it for every brand before deleting"))
                .andExpect(jsonPath("$.baseCurrencyId").value(usdId))
                .andExpect(jsonPath("$.quoteCurrencyId").value(jpyId))
                .andExpect(jsonPath("$.activeBrandCodes", org.hamcrest.Matchers.contains("PUG")));

        // nothing was deleted
        mockMvc.perform(get("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isOk());
    }

    @Test
    void delete_succeeds_onceEveryBrandRowIsInactive() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        for (CurrencyPair pair : currencyPairMapper.findAll(null, null)) {
            pair.setActive(false);
            currencyPairMapper.update(pair);
        }

        mockMvc.perform(delete("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void create_succeeds_forReverseDirection_afterOriginalDefinitionDeleted() throws Exception {
        String response = mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = objectMapper.readTree(response).get("id").asLong();

        // deactivate every provisioned row for every brand first, per the delete guard
        for (CurrencyPair pair : currencyPairMapper.findAll(null, null)) {
            pair.setActive(false);
            currencyPairMapper.update(pair);
        }

        mockMvc.perform(delete("/api/currency-pair-definitions/{id}", id))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/currency-pair-definitions").contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(jpyId, usdId, 2, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrencyCode").value("JPY"))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("USD"));
    }
}
