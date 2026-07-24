package pl.piomin.services.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import pl.piomin.services.backend.mapper.CurrencyMapper;
import pl.piomin.services.backend.model.Currency;

@SpringBootTest
@AutoConfigureMockMvc
class CurrencyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CurrencyMapper currencyMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long twdId;

    @BeforeEach
    void setUp() {
        List<Currency> existing = currencyMapper.findAll(null);
        for (Currency currency : existing) {
            currencyMapper.deleteById(currency.getId());
        }

        Currency twd = new Currency();
        twd.setCode("TWD");
        twd.setName("New Taiwan Dollar");
        twd.setNameZh("新台幣");
        twd.setSymbol("NT$");
        twd.setDecimalPlaces(0);
        twd.setActive(true);
        currencyMapper.insert(twd);
        twdId = twd.getId();

        Currency usd = new Currency();
        usd.setCode("USD");
        usd.setName("United States Dollar");
        usd.setDecimalPlaces(2);
        usd.setActive(false);
        currencyMapper.insert(usd);
    }

    @Test
    void list_returnsAllCurrencies() throws Exception {
        mockMvc.perform(get("/api/currencies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void list_filtersByActiveTrue() throws Exception {
        mockMvc.perform(get("/api/currencies").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("TWD"));
    }

    @Test
    void getById_returnsCurrency_whenFound() throws Exception {
        mockMvc.perform(get("/api/currencies/{id}", twdId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TWD"))
                .andExpect(jsonPath("$.nameZh").value("新台幣"));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/currencies/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void create_returns201_withCreatedCurrency() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("code", "KRW");
            put("name", "South Korean Won");
            put("nameZh", "韓元");
            put("symbol", "₩");
            put("decimalPlaces", 0);
            put("active", true);
        }});

        mockMvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.code").value("KRW"));
    }

    @Test
    void create_returns409_whenCodeAlreadyExists() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("code", "TWD");
            put("name", "Duplicate Dollar");
            put("decimalPlaces", 0);
        }});

        mockMvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Currency code already exists"))
                .andExpect(jsonPath("$.code").value("TWD"));
    }

    @Test
    void create_returns400_whenCodeIsInvalid() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("code", "twd");
            put("name", "Bad Code Currency");
            put("decimalPlaces", 0);
        }});

        mockMvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.code").exists());
    }

    @Test
    void create_returns400_whenRequiredFieldsMissing() throws Exception {
        String body = "{}";

        mockMvc.perform(post("/api/currencies").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.code").exists())
                .andExpect(jsonPath("$.details.name").exists())
                .andExpect(jsonPath("$.details.decimalPlaces").exists());
    }

    @Test
    void update_returns200_withUpdatedCurrency() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("name", "New Taiwan Dollar Updated");
        }});

        mockMvc.perform(put("/api/currencies/{id}", twdId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("New Taiwan Dollar Updated"))
                .andExpect(jsonPath("$.code").value("TWD"));
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("name", "Does Not Matter");
        }});

        mockMvc.perform(put("/api/currencies/{id}", 999999).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"));
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/currencies/{id}", twdId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/currencies/{id}", twdId))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns404_whenNotFound() throws Exception {
        mockMvc.perform(delete("/api/currencies/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Currency not found"));
    }
}
