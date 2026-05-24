package com.scheduling.api.integration;

import com.scheduling.api.auth.dto.LoginRequest;
import com.scheduling.api.auth.dto.RegisterRequest;
import com.scheduling.api.auth.dto.TokenResponse;
import com.scheduling.api.company.dto.CompanyRequest;
import com.scheduling.api.company.dto.CompanyResponse;
import com.scheduling.api.company.dto.CompanySettingsRequest;
import com.scheduling.api.appointment.dto.PublicAppointmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o fluxo de agendamento de ponta a ponta com banco e Redis reais.
 * Requer Docker em execução.
 */
class AppointmentFlowIT extends BaseIntegrationTest {

    // Credenciais do admin semeado pela migration V6
    private static final String ADMIN_EMAIL    = "admin@scheduling.com";
    private static final String ADMIN_PASSWORD = "password";

    private String adminToken;

    @BeforeEach
    void loginAsAdmin() {
        LoginRequest login = new LoginRequest();
        login.setEmail(ADMIN_EMAIL);
        login.setPassword(ADMIN_PASSWORD);

        ResponseEntity<TokenResponse> res = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"), login, TokenResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        adminToken = res.getBody().getAccessToken();
    }

    // ── Criação de empresa ────────────────────────────────────────────────────

    @Test
    void createCompany_asAdmin_returns200WithCompanyId() {
        ResponseEntity<CompanyResponse> res = restTemplate.exchange(
                baseUrl("/api/v1/companies"),
                HttpMethod.POST,
                new HttpEntity<>(buildCompanyRequest(), bearerHeaders(adminToken)),
                CompanyResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getId()).isPositive();
    }

    @Test
    void createCompany_unauthenticated_returns401or403() {
        ResponseEntity<Object> res = restTemplate.postForEntity(
                baseUrl("/api/v1/companies"), buildCompanyRequest(), Object.class);

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    // ── Desabilitar booking público ───────────────────────────────────────────

    @Test
    void disableClientBooking_thenPublicAppointment_returns400() {
        // 1. Admin cria empresa
        ResponseEntity<CompanyResponse> compRes = restTemplate.exchange(
                baseUrl("/api/v1/companies"),
                HttpMethod.POST,
                new HttpEntity<>(buildCompanyRequest(), bearerHeaders(adminToken)),
                CompanyResponse.class);

        assertThat(compRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        Long companyId = compRes.getBody().getId();

        // 2. Admin desabilita agendamento público via settings
        CompanySettingsRequest settings = new CompanySettingsRequest();
        settings.setAllowClientBooking(false);

        ResponseEntity<CompanyResponse> settingsRes = restTemplate.exchange(
                baseUrl("/api/v1/companies/" + companyId + "/settings"),
                HttpMethod.PUT,
                new HttpEntity<>(settings, bearerHeaders(adminToken)),
                CompanyResponse.class);
        assertThat(settingsRes.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Tenta criar agendamento público → deve retornar 400
        PublicAppointmentRequest apptReq = buildPublicRequest(companyId, 1L);
        ResponseEntity<Object> apptRes = restTemplate.postForEntity(
                baseUrl("/api/v1/appointments/public"), apptReq, Object.class);

        assertThat(apptRes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Listagem de empresas ──────────────────────────────────────────────────

    @Test
    void findAllCompanies_publicEndpoint_returns200() {
        ResponseEntity<Object[]> res = restTemplate.getForEntity(
                baseUrl("/api/v1/companies"), Object[].class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
    }

    // ── Registro de cliente e busca de perfil ─────────────────────────────────

    @Test
    void registerClientThenFetchProfile_returns200() {
        String email = "client_" + shortUuid() + "@test.com";
        RegisterRequest regReq = new RegisterRequest();
        regReq.setName("Cliente IT");
        regReq.setEmail(email);
        regReq.setPassword("senha123");

        ResponseEntity<TokenResponse> regRes = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"), regReq, TokenResponse.class);

        assertThat(regRes.getStatusCode()).isEqualTo(HttpStatus.OK);
        String clientToken = regRes.getBody().getAccessToken();

        // Acessa /users/me com o token do cliente
        ResponseEntity<Object> meRes = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(bearerHeaders(clientToken)), Object.class);

        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String shortUuid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private HttpHeaders bearerHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private CompanyRequest buildCompanyRequest() {
        CompanyRequest req = new CompanyRequest();
        req.setName("Empresa IT " + shortUuid());
        return req;
    }

    private PublicAppointmentRequest buildPublicRequest(Long companyId, Long professionalId) {
        PublicAppointmentRequest req = new PublicAppointmentRequest();
        req.setCompanyId(companyId);
        req.setProfessionalId(professionalId);
        req.setStartAt(LocalDateTime.now().plusDays(7).withHour(9).withMinute(0).withSecond(0).withNano(0));
        req.setEndAt(LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0));
        req.setClientName("Cliente Público");
        req.setClientEmail("pub_" + shortUuid() + "@test.com");
        return req;
    }
}
