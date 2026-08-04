package com.wdd.backend.controller;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
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
 * Covers the base read contract, the rate/rateType business rule, and (per
 * specs/backend/currency-pair-approval.md) the audit-workflow conversion of PUT/DELETE for
 * /api/currency-pairs: there is no POST route at all (a brand pair only ever comes from
 * specs/backend/currency-pair-definition.md's fan-out); PUT/DELETE submit a CURRENCY_PAIR audit
 * request and return 202 instead of mutating currency_pair directly; the live row is unchanged
 * until the request is approved through /api/audit-requests/{id}/approve.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrencyPairControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long brandAuId;
    private Long brandPugId;
    private Long usdId;
    private Long twdId;
    private Long eurId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_request");
        jdbcTemplate.update("DELETE FROM currency_pair");
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("DELETE FROM currency");

        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "USD", "US Dollar", 2);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "TWD", "Taiwan Dollar", 0);
        jdbcTemplate.update("INSERT INTO currency (code, name, decimal_places) VALUES (?, ?, ?)", "EUR", "Euro", 2);
        usdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'USD'", Long.class);
        twdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'TWD'", Long.class);
        eurId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'EUR'", Long.class);

        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "AU", "AU", true);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "PUG", "PUG", true);
        brandAuId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        brandPugId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'PUG'", Long.class);

        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandAuId, usdId, twdId, "32.5", "MANUAL", true);
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandAuId, eurId, twdId, null, "AUTO", false);
    }

    private Long existingPairId(Long brandId, Long baseId, Long quoteId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE brand_id = ? AND base_currency_id = ? AND quote_currency_id = ?",
                Long.class, brandId, baseId, quoteId);
    }

    // ---------- GET (unaffected by the audit-workflow delta) ----------

    @Test
    void list_returnsAllPairsWithCodesPopulated() throws Exception {
        mockMvc.perform(get("/api/currency-pairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].brandCode").exists())
                .andExpect(jsonPath("$[0].baseCurrencyCode").exists())
                .andExpect(jsonPath("$[0].quoteCurrencyCode").exists());
    }

    @Test
    void list_filtersByBrandId() throws Exception {
        mockMvc.perform(get("/api/currency-pairs").param("brandId", String.valueOf(brandAuId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void list_filtersByActive() throws Exception {
        mockMvc.perform(get("/api/currency-pairs").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rateType").value("MANUAL"));
    }

    @Test
    void getById_returnsPairWhenFound() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brandCode").value("AU"))
                .andExpect(jsonPath("$.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.quoteCurrencyCode").value("TWD"))
                .andExpect(jsonPath("$.rate").value(32.5));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/currency-pairs/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    // ---------- POST no longer exists ----------

    @Test
    void post_isNotMapped() throws Exception {
        mockMvc.perform(post("/api/currency-pairs")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().is(org.springframework.http.HttpStatus.METHOD_NOT_ALLOWED.value()));
    }

    // ---------- PUT submits a CURRENCY_PAIR/UPDATE audit request ----------

    @Test
    void update_returns202WithPendingAuditRequest_andLeavesLiveRowUnchanged() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"active\":false,\"requestedBy\":\"Alice\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("CURRENCY_PAIR"))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.requestedBy").value("Alice"))
                .andExpect(jsonPath("$.before.active").value(true))
                .andExpect(jsonPath("$.after.active").value(false))
                .andExpect(jsonPath("$.after.brandCode").value("AU"))
                .andExpect(jsonPath("$.after.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.after.quoteCurrencyCode").value("TWD"));

        // Live row is unchanged.
        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        mockMvc.perform(put("/api/currency-pairs/999999")
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_withNonexistentBrandReturns404() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"brandId\":999999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }

    @Test
    void update_withCollidingBrandBaseQuoteReturns409() throws Exception {
        Long id = existingPairId(brandAuId, eurId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content(String.format("{\"baseCurrencyId\":%d}", usdId)))
                .andExpect(status().isConflict());
    }

    @Test
    void update_withBaseEqualsQuoteReturns400() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content(String.format("{\"quoteCurrencyId\":%d}", usdId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_manualToAutoClearsRateEvenWhenRateSupplied() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"rateType\":\"AUTO\",\"rate\":123.45}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.after.rateType").value("AUTO"))
                .andExpect(jsonPath("$.after.rate").doesNotExist());
    }

    @Test
    void update_autoToManualWithoutRateReturns400() throws Exception {
        Long id = existingPairId(brandAuId, eurId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"rateType\":\"MANUAL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_toManualWithRateSuppliedSucceeds() throws Exception {
        Long id = existingPairId(brandAuId, eurId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"rateType\":\"MANUAL\",\"rate\":41.0}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.after.rateType").value("MANUAL"))
                .andExpect(jsonPath("$.after.rate").value(41.0));
    }

    @Test
    void update_secondPendingUpdateForSamePairReturns409() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("A pending audit request already exists for this entity"));
    }

    @Test
    void get_serializesNullRateForAutoPairs() throws Exception {
        Long id = existingPairId(brandAuId, eurId, twdId);

        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rateType").value("AUTO"))
                .andExpect(jsonPath("$.rate").doesNotExist());
    }

    // ---------- DELETE submits a CURRENCY_PAIR/DELETE audit request ----------

    @Test
    void delete_returns202WithPendingAuditRequest_andLeavesLiveRowUnchanged() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(delete("/api/currency-pairs/" + id))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("CURRENCY_PAIR"))
                .andExpect(jsonPath("$.actionType").value("DELETE"))
                .andExpect(jsonPath("$.entityId").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before.baseCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.after").doesNotExist());

        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isOk());
    }

    @Test
    void delete_withRequestedByBody_passesThrough() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(delete("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"requestedBy\":\"Bob\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestedBy").value("Bob"));
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_secondPendingDeleteForSamePairReturns409() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        mockMvc.perform(delete("/api/currency-pairs/" + id)).andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/currency-pairs/" + id))
                .andExpect(status().isConflict());
    }

    // ---------- Approval round-trip (via the generic /api/audit-requests endpoint) ----------

    @Test
    void approve_updateRequest_overwritesLiveRow() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        String putResponse = mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(putResponse);

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void approve_deleteRequest_removesLiveRow() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        String deleteResponse = mockMvc.perform(delete("/api/currency-pairs/" + id))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(deleteResponse);

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/currency-pairs/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_updateRequest_reValidationFailure_dueToLiveDuplicateCreatedMeanwhile_leavesPending() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        // Submit an UPDATE moving this pair to (AU, USD, EUR) — no live collision at submission
        // time, so this succeeds.
        String putResponse = mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content(String.format("{\"quoteCurrencyId\":%d}", eurId)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(putResponse);

        // Simulate a different pair acquiring the exact target triple (AU, USD, EUR) in the
        // meantime (e.g. via some other already-approved request) — re-validation at approval
        // time must now fail with 409 and leave this request PENDING.
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandAuId, usdId, eurId, "1.0", "MANUAL", true);

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void approve_updateRequest_reValidationFailure_dueToTargetRowDeletedMeanwhile_leavesPending() throws Exception {
        Long id = existingPairId(brandAuId, usdId, twdId);

        String putResponse = mockMvc.perform(put("/api/currency-pairs/" + id)
                        .contentType("application/json")
                        .content("{\"active\":false}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = extractId(putResponse);

        // Approve and apply an independent DELETE for the same row directly against the mapper,
        // simulating the row disappearing between submission and approval of the UPDATE above.
        jdbcTemplate.update("DELETE FROM currency_pair WHERE id = ?", id);

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/audit-requests/" + requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    private Long extractId(String json) {
        Number id = JsonPath.read(json, "$.id");
        return id.longValue();
    }
}
