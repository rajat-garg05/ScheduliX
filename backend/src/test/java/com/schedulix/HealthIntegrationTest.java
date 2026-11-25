package com.schedulix;

import com.schedulix.common.HealthController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HealthIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @LocalServerPort
    int port;

    @Test
    void appStartsWithPostgresAndFlywayAndReturnsHealthy() {
        RestClient client = RestClient.create("http://localhost:" + port);

        HealthController.HealthResponse response = client.get()
                .uri("/api/health")
                .retrieve()
                .body(HealthController.HealthResponse.class);

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo("ok");
    }
}