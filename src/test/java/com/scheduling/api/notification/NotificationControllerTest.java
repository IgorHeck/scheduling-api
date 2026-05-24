package com.scheduling.api.notification;

import com.scheduling.api.auth.service.TokenBlacklistService;
import com.scheduling.api.auth.service.TokenService;
import com.scheduling.api.config.SecurityConfig;
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean NotificationService   notificationService;
    @MockBean TokenService          tokenService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean UserRepository        userRepository;
    @MockBean StringRedisTemplate   stringRedisTemplate;

    // ── GET /notifications/subscribe ─────────────────────────────────────────

    /**
     * SseEmitter abre uma conexão long-lived — o MockMvc inicia o processamento async
     * mas não aguarda conclusão (a conexão fica aberta). Verificamos apenas que:
     * 1. O processamento async foi iniciado (asyncStarted).
     * 2. O serviço foi chamado com o e-mail correto.
     */
    @Test
    @WithMockUser(username = "user@test.com", roles = {"CLIENT"})
    void subscribe_authenticated_startsAsyncAndCallsService() throws Exception {
        when(notificationService.subscribe(anyString())).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted());

        verify(notificationService).subscribe("user@test.com");
    }

    @Test
    void subscribe_unauthenticated_returns4xx() throws Exception {
        // Sem autenticação, a rota protegida retorna 401 ou 403
        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void subscribe_asAdmin_callsServiceWithAdminEmail() throws Exception {
        when(notificationService.subscribe(anyString())).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .accept(MediaType.TEXT_EVENT_STREAM_VALUE))
                .andExpect(request().asyncStarted());

        verify(notificationService).subscribe("admin@test.com");
    }
}
