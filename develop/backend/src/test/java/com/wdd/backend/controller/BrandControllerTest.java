package com.wdd.backend.controller;

import static org.hamcrest.Matchers.everyItem;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM brand");
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "AU", "AU", true);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "MONETA", "MONETA", true);
        jdbcTemplate.update("INSERT INTO brand (code, name, active) VALUES (?, ?, ?)", "PUG", "PUG", false);
    }

    @Test
    void list_returnsAllBrands() throws Exception {
        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void list_filtersByActiveTrue() throws Exception {
        mockMvc.perform(get("/api/brands").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].active").value(everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    void list_filtersByActiveFalse() throws Exception {
        mockMvc.perform(get("/api/brands").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value("PUG"));
    }

    @Test
    void getById_returnsBrandWhenFound() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);

        mockMvc.perform(get("/api/brands/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AU"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getById_returns404WhenMissing() throws Exception {
        mockMvc.perform(get("/api/brands/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void update_disablesBrandAndReturns200() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        String body = "{\"active\":false}";

        mockMvc.perform(put("/api/brands/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").value("AU"));
    }

    @Test
    void update_reenablesBrandAndReturns200() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'PUG'", Long.class);
        String body = "{\"active\":true}";

        mockMvc.perform(put("/api/brands/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.code").value("PUG"));
    }

    @Test
    void update_returns400WhenActiveMissing() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        String body = "{}";

        mockMvc.perform(put("/api/brands/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.active").exists());
    }

    @Test
    void update_returns400WhenActiveInvalid() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);
        String body = "{\"active\":\"not-a-boolean\"}";

        mockMvc.perform(put("/api/brands/" + id)
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns404WhenMissing() throws Exception {
        String body = "{\"active\":false}";

        mockMvc.perform(put("/api/brands/999999")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }

    @Test
    void create_isNotSupported() throws Exception {
        String body = "{\"code\":\"NEW\",\"name\":\"New Brand\",\"active\":true}";

        mockMvc.perform(post("/api/brands")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void delete_isNotSupported() throws Exception {
        Long id = jdbcTemplate.queryForObject("SELECT id FROM brand WHERE code = 'AU'", Long.class);

        mockMvc.perform(delete("/api/brands/" + id))
                .andExpect(status().isMethodNotAllowed());
    }
}
