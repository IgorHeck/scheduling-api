package com.scheduling.api.integration;

import com.scheduling.api.auth.dto.RegisterRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o rate limiting por IP em endpoints públicos sensíveis.
 * Usa Redis real via Testcontainers.
 *
 * IMPORTANTE: os limites são por IP/minuto. Todos os testes rodam com
 * o mesmo IP (127.0.0.1 ou ::1), então são executados sequencialmente
 * em um único método para evitar interferência entre testes.
 *
 * Requer Docker em execução.
 */
class RateLimitIT extends BaseIntegrationTest {

    /**
     * POST /auth/register permite 5 req/min/IP.
     * O 6º deve retornar 429.
     */
    @Test
    void register_after5Requests_6thReturns429() {
        // Registros 1-5: todos devem passar (200 ou 400 por email duplicado, não 429)
        for (int i = 1; i <= 5; i++) {
            RegisterRequest req = buildRegisterRequest("rl_reg_" + i + "_" + shortUuid() + "@test.com");
            ResponseEntity<Object> res = restTemplate.postForEntity(
                    baseUrl("/api/v1/auth/register"), req, Object.class);

            assertThat(res.getStatusCode().value())
                    .as("Requisição %d deve passar pelo rate limiter", i)
                    .isNotEqualTo(429);
        }

        // 6ª requisição: deve ser bloqueada pelo rate limiter
        RegisterRequest req6 = buildRegisterRequest("rl_6_" + shortUuid() + "@test.com");
        ResponseEntity<Object> res6 = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"), req6, Object.class);

        assertThat(res6.getStatusCode().value())
                .as("6ª requisição deve ser bloqueada (429 Too Many Requests)")
                .isEqualTo(429);
    }

    /**
     * POST /appointments/public permite 10 req/min/IP.
     * O 11º deve retornar 429.
     *
     * Nota: os requests falharão com 400 (empresa/profissional inválidos),
     * mas o rate limiter aplica ANTES da validação de negócio.
     * As primeiras 10 são processadas (mesmo retornando 400),
     * a 11ª é bloqueada com 429.
     */
    @Test
    void publicAppointment_after10Requests_11thReturns429() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // payload inválido — vai falhar no service, mas passa pelo rate limiter
        String invalidBody = """
                {
                  "companyId": 99999,
                  "professionalId": 99999,
                  "startAt": "2030-01-01T10:00:00",
                  "endAt": "2030-01-01T11:00:00",
                  "clientName": "Teste",
                  "clientEmail": "rl@test.com"
                }
                """;

        for (int i = 1; i <= 10; i++) {
            ResponseEntity<Object> res = restTemplate.exchange(
                    baseUrl("/api/v1/appointments/public"),
                    HttpMethod.POST,
                    new HttpEntity<>(invalidBody, headers),
                    Object.class);

            assertThat(res.getStatusCode().value())
                    .as("Requisição %d deve passar pelo rate limiter (pode retornar 400)", i)
                    .isNotEqualTo(429);
        }

        // 11ª requisição: bloqueada
        ResponseEntity<Object> res11 = restTemplate.exchange(
                baseUrl("/api/v1/appointments/public"),
                HttpMethod.POST,
                new HttpEntity<>(invalidBody, headers),
                Object.class);

        assertThat(res11.getStatusCode().value())
                .as("11ª requisição deve ser bloqueada (429 Too Many Requests)")
                .isEqualTo(429);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private RegisterRequest buildRegisterRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setName("Teste Rate Limit");
        req.setEmail(email);
        req.setPassword("senha123");
        return req;
    }
}
