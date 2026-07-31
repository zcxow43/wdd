package pl.piomin.services.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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

import pl.piomin.services.backend.audit.AuditRequestMapper;
import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.mapper.CurrencyPairMapper;
import pl.piomin.services.backend.model.Brand;
import pl.piomin.services.backend.model.Currency;
import pl.piomin.services.backend.model.CurrencyPair;

/**
 * Integration tests for {@code /api/currency-pairs}. Per
 * specs/backend/currency-pair.md's "remove the create endpoint" delta, there
 * is no {@code POST} route at all - a brand's {@code currency_pair} row can
 * only come into existence via a global currency-pair-definition's fan-out
 * (specs/backend/currency-pair-definition.md). PUT/DELETE no longer mutate
 * {@code currency_pair} directly - they return {@code 202} with a pending
 * {@code AuditRequestResponse}, and the change only lands once approved via
 * the generic {@code /api/audit-requests/{id}/approve} endpoint. GET remains
 * unaffected and is tested exactly as before.
 */
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
    private AuditRequestMapper auditRequestMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long pugId;
    private Long starId;
    private Long usdId;
    private Long twdId;
    private Long pairId;

    @BeforeEach
    void setUp() {
        for (Long id : auditRequestMapper.findAllIds()) {
            auditRequestMapper.deleteById(id);
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
        currencyMapper.insert(currency);
        return currency.getId();
    }

    // --- GET (unaffected by the approval delta) --------------------------------

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
    void post_isNotMapped() throws Exception {
        // Per specs/backend/currency-pair.md's delta, POST /api/currency-pairs no
        // longer exists at all - not even behind approval. A brand's currency_pair
        // row can only come into existence via a global currency-pair-definition's
        // fan-out (specs/backend/currency-pair-definition.md). Spring's default
        // "no handler mapped" behavior applies here (405, since GET/PUT/DELETE are
        // mapped on this same path).
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("brandId", starId);
            put("baseCurrencyId", usdId);
            put("quoteCurrencyId", twdId);
            put("rate", 33.1);
            put("rateType", "MANUAL");
        }});

        mockMvc.perform(post("/api/currency-pairs").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    // --- PUT (submits an UPDATE audit request) ---------------------------------

    @Test
    void update_returns202_withBeforeAndMergedAfter_andLivePairUnchanged() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 40.0);
            put("requestedBy", "Alice");
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("CURRENCY_PAIR"))
                .andExpect(jsonPath("$.actionType").value("UPDATE"))
                .andExpect(jsonPath("$.entityId").value(pairId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before.rate").value(32.5))
                .andExpect(jsonPath("$.before.brandCode").value("PUG"))
                .andExpect(jsonPath("$.after.rate").value(40.0))
                .andExpect(jsonPath("$.after.brandCode").value("PUG"))
                .andExpect(jsonPath("$.requestedBy").value("Alice"));

        CurrencyPair live = currencyPairMapper.findById(pairId);
        org.assertj.core.api.Assertions.assertThat(live.getRate()).isEqualByComparingTo("32.5");
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
    void update_returns409_whenPendingUpdateAlreadyExists() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 40.0);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void update_clearsRateToNull_whenSwitchingManualToAuto() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rateType", "AUTO");
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.after.rate").value(nullValue()))
                .andExpect(jsonPath("$.after.rateType").value("AUTO"));
    }

    @Test
    void update_returns400_whenSwitchingAutoToManualWithoutRate() throws Exception {
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
    void update_returns400_whenNewBaseEqualsQuote() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("baseCurrencyId", twdId);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Base and quote currency must differ"));
    }

    @Test
    void update_returns409_whenCollidesWithAnotherLiveRow() throws Exception {
        CurrencyPair other = new CurrencyPair();
        other.setBrandId(pugId);
        other.setBaseCurrencyId(twdId);
        other.setQuoteCurrencyId(usdId);
        other.setRate(new BigDecimal("1.0"));
        other.setRateType("MANUAL");
        other.setActive(true);
        currencyPairMapper.insert(other);

        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("baseCurrencyId", twdId);
            put("quoteCurrencyId", usdId);
        }});

        mockMvc.perform(put("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    // --- DELETE (submits a DELETE audit request) --------------------------------

    @Test
    void delete_returns202_withBeforeSnapshot_andPairStillExists() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.entityType").value("CURRENCY_PAIR"))
                .andExpect(jsonPath("$.actionType").value("DELETE"))
                .andExpect(jsonPath("$.entityId").value(pairId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.before.brandCode").value("PUG"))
                .andExpect(jsonPath("$.after").value(nullValue()));

        mockMvc.perform(get("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isOk());
    }

    @Test
    void delete_withRequestedBy_returns202() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("requestedBy", "Bob");
        }});

        mockMvc.perform(delete("/api/currency-pairs/{id}", pairId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestedBy").value("Bob"));
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency pair not found"));
    }

    @Test
    void delete_returns409_whenPendingDeleteAlreadyExists() throws Exception {
        mockMvc.perform(delete("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isConflict());
    }

    // --- Approval round-trip (via the generic /api/audit-requests endpoints) ---

    @Test
    void approve_updateRequest_overwritesLivePair() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 50.0);
        }});

        String response = mockMvc.perform(put("/api/currency-pairs/{id}", pairId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        CurrencyPair live = currencyPairMapper.findById(pairId);
        org.assertj.core.api.Assertions.assertThat(live.getRate()).isEqualByComparingTo("50.0");
    }

    @Test
    void approve_deleteRequest_deletesLivePair() throws Exception {
        String response = mockMvc.perform(delete("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(get("/api/currency-pairs/{id}", pairId))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_updateRequest_returns409_andLeavesPending_whenDuplicateNowExistsAtApprovalTime() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("baseCurrencyId", twdId);
            put("quoteCurrencyId", usdId);
        }});

        String response = mockMvc.perform(put("/api/currency-pairs/{id}", pairId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        // Someone else creates the colliding live row directly after submission.
        CurrencyPair other = new CurrencyPair();
        other.setBrandId(pugId);
        other.setBaseCurrencyId(twdId);
        other.setQuoteCurrencyId(usdId);
        other.setRate(new BigDecimal("1.0"));
        other.setRateType("MANUAL");
        other.setActive(true);
        currencyPairMapper.insert(other);

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/audit-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // live pair (id=pairId) unaffected
        CurrencyPair live = currencyPairMapper.findById(pairId);
        org.assertj.core.api.Assertions.assertThat(live.getBaseCurrencyId()).isEqualTo(usdId);
    }

    @Test
    void approve_updateRequest_returns404_andLeavesPending_whenTargetPairDeletedBeforeApproval() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("rate", 60.0);
        }});

        String response = mockMvc.perform(put("/api/currency-pairs/{id}", pairId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = objectMapper.readTree(response).get("id").asLong();

        // Row removed directly (bypassing audit) between submission and approval.
        currencyPairMapper.deleteById(pairId);

        mockMvc.perform(post("/api/audit-requests/{id}/approve", requestId))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/audit-requests/{id}", requestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
