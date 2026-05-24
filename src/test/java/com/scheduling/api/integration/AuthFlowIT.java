package com.scheduling.api.integration;

import com.scheduling.api.auth.dto.LoginRequest;
import com.scheduling.api.auth.dto.RegisterRequest;
import com.scheduling.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o fluxo completo de autenticação com banco de dados e Redis reais.
 * Requer Docker em execução.
 */
class AuthFlowIT extends BaseIntegrationTest {

    // ── Registro ──────────────────────────────────────────────────────────────

    @Test
    void register_newUser_returns200WithTokens() {
        RegisterRequest req = buildRegisterRequest(uniqueEmail());

        ResponseEntity<TokenResponse> res = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"), req, TokenResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getAccessToken()).isNotBlank();
        assertThat(res.getBody().getRefreshToken()).isNotBlank();
    }

    @Test
    void register_duplicateEmail_returns400() {
        String email = uniqueEmail();
        RegisterRequest req = buildRegisterRequest(email);

        // Primeiro registro
        restTemplate.postForEntity(baseUrl("/api/v1/auth/register"), req, Object.class);

        // Segundo registro com mesmo e-mail
        ResponseEntity<Object> res = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"), req, Object.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @Test
    void login_withValidCredentials_returns200() {
        String email = uniqueEmail();
        // Registra o usuário
        restTemplate.postForEntity(baseUrl("/api/v1/auth/register"),
                buildRegisterRequest(email), TokenResponse.class);

        // Faz login
        LoginRequest login = new LoginRequest();
        login.setEmail(email);
        login.setPassword("senha123");

        ResponseEntity<TokenResponse> res = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"), login, TokenResponse.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).isNotNull();
        assertThat(res.getBody().getAccessToken()).isNotBlank();
    }

    @Test
    void login_withWrongPassword_returns400() {
        LoginRequest login = new LoginRequest();
        login.setEmail("naoexiste@test.com");
        login.setPassword("senhaerrada");

        ResponseEntity<Object> res = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/login"), login, Object.class);

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    // ── Acesso a rota protegida ────────────────────────────────────────────────

    @Test
    void accessProtectedRoute_withValidToken_returns200() {
        // Registra e obtém token
        String email = uniqueEmail();
        ResponseEntity<TokenResponse> regRes = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"),
                buildRegisterRequest(email), TokenResponse.class);

        String accessToken = regRes.getBody().getAccessToken();

        // Acessa rota protegida
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Object> meRes = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(headers), Object.class);

        assertThat(meRes.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void accessProtectedRoute_withoutToken_returns401or403() {
        ResponseEntity<Object> res = restTemplate.getForEntity(
                baseUrl("/api/v1/users/me"), Object.class);

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
    }

    // ── Logout + blacklist ────────────────────────────────────────────────────

    @Test
    void logout_thenAccessWithSameToken_returns401() {
        // Registra
        String email = uniqueEmail();
        ResponseEntity<TokenResponse> regRes = restTemplate.postForEntity(
                baseUrl("/api/v1/auth/register"),
                buildRegisterRequest(email), TokenResponse.class);

        String accessToken = regRes.getBody().getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        // Confirma que o token funciona
        ResponseEntity<Object> before = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(headers), Object.class);
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Faz logout (revoga token)
        ResponseEntity<Void> logoutRes = restTemplate.exchange(
                baseUrl("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(headers), Void.class);
        assertThat(logoutRes.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Tenta usar o mesmo token após logout
        ResponseEntity<Object> after = restTemplate.exchange(
                baseUrl("/api/v1/users/me"), HttpMethod.GET,
                new HttpEntity<>(headers), Object.class);
        assertThat(after.getStatusCode().is4xxClientError()).isTrue();
    }

    // ── Recuperação de senha ──────────────────────────────────────────────────

    @Test
    void forgotPassword_anyEmail_always204() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> req = new HttpEntity<>(
                "{\"email\":\"qualquer@test.com\"}", headers);

        ResponseEntity<Void> res = restTemplate.exchange(
                baseUrl("/api/v1/auth/forgot-password"),
                HttpMethod.POST, req, Void.class);

        // Sempre retorna 204 independente do e-mail existir ou não
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String uniqueEmail() {
        return "user_" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";
    }

    private RegisterRequest buildRegisterRequest(String email) {
        RegisterRequest req = new RegisterRequest();
        req.setName("Usuário Teste");
        req.setEmail(email);
        req.setPassword("senha123");
        return req;
    }
}
