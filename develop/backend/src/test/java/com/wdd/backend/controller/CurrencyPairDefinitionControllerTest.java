package com.wdd.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration coverage for /api/currency-pair-definitions (specs/backend/currency-pair-
 * definition.md): base CRUD plus the per-brand fan-out on create and the "every brand's pair
 * must be inactive first" delete guard. Applies immediately — no audit-approval round trip.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrencyPairDefinitionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long usdId;
    private Long jpyId;
    private Long eurId;
    private Long brandAuId;
    private Long brandPugId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_request");
        jdbcTemplate.update("DELETE FROM currency_pair_definition");
        jdbcTemplate.update("DELETE FROM currency_pair");
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("DELETE FROM currency");

        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "USD", "US Dollar", 2);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "JPY", "Japanese Yen", 0);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "EUR", "Euro", 2);
        usdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'USD'", Long.class);
        jpyId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'JPY'", Long.class);
        eurId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'EUR'", Long.class);

        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "AU", "AU", true);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "PUG", "PUG", true);
        brandAuId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        brandPugId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'PUG'", Long.class);
    }

    private String createBody(Long baseId, Long quoteId, int forwardPrecision, int reversePrecision) {
        return String.format("{\"baseCurrencyId\":%d,\"quoteCurrencyId\":%d,\"forwardPrecision\":%d,\"reversePrecision\":%d}",
                baseId, quoteId, forwardPrecision, reversePrecision);
    }

    private Integer currencyPairCountFor(Long brandId, Long baseId, Long quoteId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Integer.class, brandId, baseId, quoteId);
    }

    private void deactivateAllPairsFor(Long baseId, Long quoteId) {
        jdbcTemplate.update("UPDATE currency_pair SET active = false WHERE base_currency_id = ? AND quote_currency_id = ?",
                baseId, quoteId);
    }

    private Long extractId(String json) {
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }

    // ---------- create / fan-out ----------

    @Test
    void create_provisionsCurrencyPairForEverySeededBrand() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrencyId").value(usdId))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.quoteCurrencyId").value(jpyId))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("JPY"))
                .andExpect(jsonPath("$.forwardPrecision").value(2))
                .andExpect(jsonPath("$.reversePrecision").value(5));

        assertProvisionedRow(brandAuId);
        assertProvisionedRow(brandPugId);
    }

    private void assertProvisionedRow(Long brandId) {
        String rateType = jdbcTemplate.queryForObject(
                "SELECT rate_type FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                String.class, brandId, usdId, jpyId);
        Boolean active = jdbcTemplate.queryForObject(
                "SELECT active FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Boolean.class, brandId, usdId, jpyId);
        String rate = jdbcTemplate.queryForObject(
                "SELECT rate FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                String.class, brandId, usdId, jpyId);
        org.assertj.core.api.Assertions.assertThat(rateType).isEqualTo("AUTO");
        org.assertj.core.api.Assertions.assertThat(rate).isNull();
        org.assertj.core.api.Assertions.assertThat(active).isTrue();
    }

    @Test
    void create_leavesExistingBrandRowUntouched_whileStillProvisioningOtherBrand() throws Exception {
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandPugId, usdId, jpyId, "150.25", "MANUAL", false);

        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        // PUG's existing row is untouched.
        String pugRateType = jdbcTemplate.queryForObject(
                "SELECT rate_type FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                String.class, brandPugId, usdId, jpyId);
        Boolean pugActive = jdbcTemplate.queryForObject(
                "SELECT active FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Boolean.class, brandPugId, usdId, jpyId);
        org.assertj.core.api.Assertions.assertThat(pugRateType).isEqualTo("MANUAL");
        org.assertj.core.api.Assertions.assertThat(pugActive).isFalse();
        org.assertj.core.api.Assertions.assertThat(currencyPairCountFor(brandPugId, usdId, jpyId)).isEqualTo(1);

        // AU still gets provisioned.
        assertProvisionedRow(brandAuId);
    }

    @Test
    void create_reverseDirectionAfterExistingDefinitionReturns409_andInsertsNothing() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(jpyId, usdId, 3, 3)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "A currency pair definition already exists for this pair or its reverse direction"));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM currency_pair_definition", Integer.class);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }

    @Test
    void create_exactSameDirectionTwiceReturns409() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 1, 1)))
                .andExpect(status().isConflict());
    }

    @Test
    void create_baseEqualsQuoteReturns400() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, usdId, 2, 5)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_nonexistentCurrencyReturns404() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(999999L, jpyId, 2, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"));
    }

    @Test
    void create_precisionOutOfRangeReturns400() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 9, 5)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, -1)))
                .andExpect(status().isBadRequest());
    }

    // ---------- list / getById ----------

    @Test
    void list_filtersByBaseAndQuoteCurrencyId() throws Exception {
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(eurId, jpyId, 2, 4)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/currency-pair-definitions").param("baseCurrencyId", String.valueOf(usdId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].baseCurrencyCode").value("USD"));

        mockMvc.perform(get("/api/currency-pair-definitions").param("quoteCurrencyId", String.valueOf(jpyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/currency-pair-definitions/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair definition not found"));
    }

    // ---------- update ----------

    @Test
    void update_updatesPrecisionOnly() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        mockMvc.perform(put("/api/currency-pair-definitions/" + id)
                        .contentType("application/json")
                        .content("{\"forwardPrecision\":3,\"reversePrecision\":6}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.forwardPrecision").value(3))
                .andExpect(jsonPath("$.reversePrecision").value(6))
                .andExpect(jsonPath("$.baseCurrencyId").value(usdId))
                .andExpect(jsonPath("$.quoteCurrencyId").value(jpyId));
    }

    @Test
    void update_outOfRangePrecisionReturns400() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        mockMvc.perform(put("/api/currency-pair-definitions/" + id)
                        .contentType("application/json")
                        .content("{\"forwardPrecision\":9,\"reversePrecision\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        mockMvc.perform(put("/api/currency-pair-definitions/999999")
                        .contentType("application/json")
                        .content("{\"forwardPrecision\":3,\"reversePrecision\":6}"))
                .andExpect(status().isNotFound());
    }

    // ---------- delete ----------

    @Test
    void delete_removesDefinition_butLeavesProvisionedCurrencyPairsUntouched() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        // The delete guard requires every brand's row to be inactive first.
        deactivateAllPairsFor(usdId, jpyId);

        mockMvc.perform(delete("/api/currency-pair-definitions/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/currency-pair-definitions/" + id))
                .andExpect(status().isNotFound());

        // Provisioned currency_pair rows remain, unchanged (still visible via GET /api/currency-pairs).
        org.assertj.core.api.Assertions.assertThat(currencyPairCountFor(brandAuId, usdId, jpyId)).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(currencyPairCountFor(brandPugId, usdId, jpyId)).isEqualTo(1);

        mockMvc.perform(get("/api/currency-pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/api/currency-pair-definitions/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns409WithActiveBrandCodes_whenAnyBrandStillActive() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        // Deactivate only AU's row, leaving PUG's row active.
        jdbcTemplate.update(
                "UPDATE currency_pair SET active = false WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                brandAuId, usdId, jpyId);

        mockMvc.perform(delete("/api/currency-pair-definitions/" + id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "One or more brands still have this currency pair active; disable it for every brand before deleting"))
                .andExpect(jsonPath("$.baseCurrencyId").value(usdId))
                .andExpect(jsonPath("$.quoteCurrencyId").value(jpyId))
                .andExpect(jsonPath("$.activeBrandCodes[0]").value("PUG"))
                .andExpect(jsonPath("$.activeBrandCodes.length()").value(1));

        // Nothing was deleted.
        mockMvc.perform(get("/api/currency-pair-definitions/" + id))
                .andExpect(status().isOk());
    }

    @Test
    void delete_succeeds_onceEveryBrandRowIsInactive() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        deactivateAllPairsFor(usdId, jpyId);

        mockMvc.perform(delete("/api/currency-pair-definitions/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_succeeds_whenZeroCurrencyPairRowsExist() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        // All provisioned rows are independently deleted — absence of a row never blocks
        // deletion, only an active one does.
        jdbcTemplate.update("DELETE FROM currency_pair WHERE base_currency_id = ? AND quote_currency_id = ?",
                usdId, jpyId);

        mockMvc.perform(delete("/api/currency-pair-definitions/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    void create_succeedsForReverseDirection_afterOriginalDefinitionDeleted() throws Exception {
        String createResponse = mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(usdId, jpyId, 2, 5)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long id = extractId(createResponse);

        deactivateAllPairsFor(usdId, jpyId);

        mockMvc.perform(delete("/api/currency-pair-definitions/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/currency-pair-definitions")
                        .contentType("application/json")
                        .content(createBody(jpyId, usdId, 3, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.baseCurrencyCode").value("JPY"))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("USD"));
    }
}
