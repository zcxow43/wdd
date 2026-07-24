package pl.piomin.services.backend.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import pl.piomin.services.backend.mapper.BrandMapper;
import pl.piomin.services.backend.model.Brand;

@SpringBootTest
@AutoConfigureMockMvc
class BrandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BrandMapper brandMapper;

    @Autowired
    private ObjectMapper objectMapper;

    private Long auId;

    @BeforeEach
    void setUp() {
        List<Brand> existing = brandMapper.findAll(null);
        for (Brand brand : existing) {
            brandMapper.deleteById(brand.getId());
        }
    }

    private Long insertBrand(String code, boolean active) {
        Brand brand = new Brand();
        brand.setCode(code);
        brand.setName(code);
        brand.setActive(active);
        brandMapper.insert(brand);
        return brand.getId();
    }

    @Test
    void list_returnsAllBrands() throws Exception {
        insertBrand("AU", true);
        insertBrand("MONETA", false);

        mockMvc.perform(get("/api/brands"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void list_filtersByActiveTrue() throws Exception {
        insertBrand("AU", true);
        insertBrand("MONETA", false);

        mockMvc.perform(get("/api/brands").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("AU"));
    }

    @Test
    void getById_returnsBrand_whenFound() throws Exception {
        auId = insertBrand("AU", true);

        mockMvc.perform(get("/api/brands/{id}", auId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AU"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getById_returns404_whenNotFound() throws Exception {
        mockMvc.perform(get("/api/brands/{id}", 999999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"))
                .andExpect(jsonPath("$.id").value(999999));
    }

    @Test
    void update_disablesBrand_returns200() throws Exception {
        auId = insertBrand("AU", true);
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("active", false);
        }});

        mockMvc.perform(put("/api/brands/{id}", auId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").value("AU"));
    }

    @Test
    void update_enablesBrand_returns200() throws Exception {
        auId = insertBrand("AU", false);
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("active", true);
        }});

        mockMvc.perform(put("/api/brands/{id}", auId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void update_returns400_whenActiveMissing() throws Exception {
        auId = insertBrand("AU", true);
        String body = "{}";

        mockMvc.perform(put("/api/brands/{id}", auId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"))
                .andExpect(jsonPath("$.details.active").exists());
    }

    @Test
    void update_returns400_whenActiveInvalid() throws Exception {
        auId = insertBrand("AU", true);
        String body = "{\"active\": \"not-a-boolean\"}";

        mockMvc.perform(put("/api/brands/{id}", auId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returns404_whenNotFound() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<>() {{
            put("active", false);
        }});

        mockMvc.perform(put("/api/brands/{id}", 999999).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Brand not found"));
    }
}
