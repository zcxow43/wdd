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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM audit_request");
        jdbcTemplate.update("DELETE FROM currency_pair");
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("DELETE FROM currency");
        jdbcTemplate.update("INSERT INTO currency (code, name, name_zh, symbol, decimal_places) VALUES (?, ?, ?, ?, ?)",
                "TWD", "New Taiwan Dollar", "新台幣", "NT$", 0);
        jdbcTemplate.update("INSERT INTO currency (code, name, name_zh, symbol, decimal_places) VALUES (?, ?, ?, ?, ?)",
                "USD", "United States Dollar", "美元", "$", 2);
    }

    @Test
    void list_returnsAllCurrencies() throws Exception {
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].active").doesNotExist());
    }

    @Test
    void getById_returnsCurrencyWhenFound() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'TWD'", Long.class);

        mockMvc.perform(get("/api/currencies/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TWD"))
                .andExpect(jsonPath("$.nameZh").value("新台幣"))
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/currencies/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void create_createsAndReturns201() throws Exception {
        String body = "{\"code\":\"KRW\",\"name\":\"South Korean Won\",\"nameZh\":\"韓元\",\"symbol\":\"₩\",\"decimalPlaces\":0}";

        mockMvc.perform(post("/api/currencies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("KRW"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void create_withDuplicateCodeReturns409() throws Exception {
        String body = "{\"code\":\"TWD\",\"name\":\"Dup\",\"decimalPlaces\":0}";

        mockMvc.perform(post("/api/currencies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Currency code already exists"))
                .andExpect(jsonPath("$.code").value("TWD"));
    }

    @Test
    void create_withInvalidCodeReturns400() throws Exception {
        String body = "{\"code\":\"twd1\",\"name\":\"Bad\",\"decimalPlaces\":0}";

        mockMvc.perform(post("/api/currencies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.code").exists());
    }

    @Test
    void create_withMissingNameReturns400() throws Exception {
        String body = "{\"code\":\"KRW\",\"decimalPlaces\":0}";

        mockMvc.perform(post("/api/currencies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.name").exists());
    }

    @Test
    void create_ignoresUnknownActiveField() throws Exception {
        String body = "{\"code\":\"KRW\",\"name\":\"South Korean Won\",\"decimalPlaces\":0,\"active\":false}";

        mockMvc.perform(post("/api/currencies")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void update_updatesAndReturns200() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'TWD'", Long.class);
        String body = "{\"name\":\"Updated Dollar\"}";

        mockMvc.perform(put("/api/currencies/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Dollar"))
                .andExpect(jsonPath("$.code").value("TWD"));
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        String body = "{\"name\":\"Doesn't matter\"}";

        mockMvc.perform(put("/api/currencies/999999")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_deletesAndReturns204() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'USD'", Long.class);

        mockMvc.perform(delete("/api/currencies/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/currencies/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns404WhenMissing() throws Exception {
        mockMvc.perform(delete("/api/currencies/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_ignoresCodeField_evenWhenSuppliedInRequestBody() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'TWD'", Long.class);
        String body = "{\"code\":\"XYZ\",\"name\":\"Still Taiwan Dollar\"}";

        mockMvc.perform(put("/api/currencies/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TWD"))
                .andExpect(jsonPath("$.name").value("Still Taiwan Dollar"));
    }

    @Test
    void delete_returns409_whenReferencedByCurrencyPair() throws Exception {
        Long usdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'USD'", Long.class);
        Long twdId = jdbcTemplate.queryForObject("SELECT id FROM currency WHERE code = 'TWD'", Long.class);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "AU", "AU", true);
        Long brandId = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        jdbcTemplate.update(
                "INSERT INTO currency_pair (brand_id, base_currency_id, quote_currency_id, rate, rate_type, active) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                brandId, usdId, twdId, "32.5", "MANUAL", true);

        mockMvc.perform(delete("/api/currencies/" + usdId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "Currency is referenced by one or more currency pairs and cannot be deleted"))
                .andExpect(jsonPath("$.id").value(usdId));

        // Remove the referencing pair via the real currency-pair endpoint (not a raw JDBC
        // delete) so the MyBatis session-level local cache backing existsByCurrencyId — scoped
        // to this @Transactional test's single connection/session — is correctly invalidated.
        // DELETE /api/currency-pairs/{id} only submits a CURRENCY_PAIR/DELETE audit request now
        // (specs/backend/currency-pair-approval.md) — it must be approved before the pair is
        // actually removed from the live table.
        Long pairId = jdbcTemplate.queryForObject(
                "SELECT id FROM currency_pair WHERE brand_id = ? AND base_currency_id = ?", Long.class,
                brandId, usdId);
        String deleteResponse = mockMvc.perform(delete("/api/currency-pairs/" + pairId))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        Long requestId = ((Number) JsonPath.read(deleteResponse, "$.id")).longValue();

        mockMvc.perform(post("/api/audit-requests/" + requestId + "/approve")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(delete("/api/currencies/" + usdId))
                .andExpect(status().isNoContent());
    }
}
