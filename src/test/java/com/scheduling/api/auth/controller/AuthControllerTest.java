package com.scheduling.api.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.api.auth.dto.*;
import com.scheduling.api.auth.service.AuthService;
import com.scheduling.api.auth.service.PasswordResetService;
import com.scheduling.api.auth.service.TokenBlacklistService;
import com.scheduling.api.auth.service.TokenService;
import com.scheduling.api.config.SecurityConfig;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.redis.core.ValueOperations;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class AuthControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    // Beans específicos do controller
    @MockBean AuthService          authService;
    @MockBean PasswordResetService passwordResetService;

    // Dependências do SecurityConfig + filtros
    @MockBean TokenService          tokenService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean UserRepository        userRepository;
    @MockBean StringRedisTemplate   stringRedisTemplate;

    /** Configura o mock do Redis para que o RateLimitFilter não lance NullPointerException. */
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUpRateLimit() {
        ValueOperations<String, String> mockOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(any())).thenReturn(1L);
        lenient().when(stringRedisTemplate.expire(any(), anyLong(), any())).thenReturn(true);
    }

    // ── POST /auth/register ──────────────────────────────────────────────────

    @Test
    void register_validRequest_returns200WithTokens() throws Exception {
        RegisterRequest req = buildRegisterRequest("João", "joao@test.com", "senha123");
        TokenResponse   tr  = buildTokenResponse("access.tok", "refresh.tok");

        when(authService.register(any())).thenReturn(tr);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.tok"))
                .andExpect(jsonPath("$.refreshToken").value("refresh.tok"));
    }

    @Test
    void register_duplicateEmail_returns400() throws Exception {
        RegisterRequest req = buildRegisterRequest("João", "dup@test.com", "senha123");

        when(authService.register(any())).thenThrow(new BusinessException("Email já cadastrado"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email já cadastrado"));
    }

    @Test
    void register_missingEmail_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"João\",\"password\":\"senha123\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/login ─────────────────────────────────────────────────────

    @Test
    void login_validCredentials_returns200() throws Exception {
        LoginRequest  req = buildLoginRequest("user@test.com", "senha123");
        TokenResponse tr  = buildTokenResponse("access", "refresh");

        when(authService.login(any())).thenReturn(tr);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access"));
    }

    @Test
    void login_wrongPassword_returns400() throws Exception {
        LoginRequest req = buildLoginRequest("user@test.com", "errada");

        when(authService.login(any())).thenThrow(new BusinessException("Credenciais inválidas"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/logout ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "user@test.com")
    void logout_authenticated_returns204AndCallsService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isNoContent());

        verify(authService).logout(eq("user@test.com"), any());
    }

    @Test
    void logout_unauthenticated_returns4xx() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().is4xxClientError());
    }

    // ── POST /auth/forgot-password ───────────────────────────────────────────

    @Test
    void forgotPassword_anyEmail_returns204() throws Exception {
        ForgotPasswordRequest req = new ForgotPasswordRequest();
        req.setEmail("anyone@test.com");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(passwordResetService).requestReset("anyone@test.com");
    }

    @Test
    void forgotPassword_invalidEmailFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /auth/reset-password ────────────────────────────────────────────

    @Test
    void resetPassword_validToken_returns204() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("valid-uuid-token");
        req.setNewPassword("novasenha123");

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNoContent());

        verify(passwordResetService).resetPassword("valid-uuid-token", "novasenha123");
    }

    @Test
    void resetPassword_invalidToken_returns400() throws Exception {
        ResetPasswordRequest req = new ResetPasswordRequest();
        req.setToken("bad-token");
        req.setNewPassword("novasenha123");

        doThrow(new BusinessException("Link inválido ou expirado. Solicite um novo."))
                .when(passwordResetService).resetPassword(any(), any());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Link inválido ou expirado. Solicite um novo."));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private RegisterRequest buildRegisterRequest(String name, String email, String password) {
        RegisterRequest req = new RegisterRequest();
        req.setName(name);
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private LoginRequest buildLoginRequest(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private TokenResponse buildTokenResponse(String access, String refresh) {
        return TokenResponse.builder()
                .accessToken(access).refreshToken(refresh)
                .tokenType("Bearer").expiresIn(900L)
                .build();
    }
}
