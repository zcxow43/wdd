package com.wdd.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthControllerTest {

    @LocalServerPort
    private int port;

    @Test
    void healthEndpointReturnsUp() {
        TestRestTemplate restTemplate = new TestRestTemplate();
        String url = "http://localhost:" + port + "/api/health";

        String body = restTemplate.getForObject(url, String.class);

        assertThat(body).contains("UP");
    }

}
