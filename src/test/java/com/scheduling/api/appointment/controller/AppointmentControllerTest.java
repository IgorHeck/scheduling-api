package com.scheduling.api.appointment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scheduling.api.appointment.dto.AppointmentResponse;
import com.scheduling.api.appointment.dto.CreateAppointmentRequest;
import com.scheduling.api.appointment.dto.RescheduleRequest;
import com.scheduling.api.appointment.model.AppointmentStatus;
import com.scheduling.api.appointment.service.AppointmentService;
import com.scheduling.api.auth.service.TokenBlacklistService;
import com.scheduling.api.auth.service.TokenService;
import com.scheduling.api.company.model.Company;
import com.scheduling.api.config.SecurityConfig;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AppointmentController.class)
@Import(SecurityConfig.class)
class AppointmentControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean AppointmentService    appointmentService;
    @MockBean UserRepository        userRepository;    // usado por resolveUser() e SecurityConfig
    @MockBean TokenService          tokenService;
    @MockBean TokenBlacklistService tokenBlacklistService;
    @MockBean StringRedisTemplate   stringRedisTemplate;

    private User  adminUser;
    private User  clientUser;
    private Company company;

    @BeforeEach
    void setUp() {
        // Configura o mock do Redis para que o RateLimitFilter não lance NullPointerException
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> mockOps = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(mockOps);
        when(mockOps.increment(any())).thenReturn(1L);
        lenient().when(stringRedisTemplate.expire(any(), anyLong(), any())).thenReturn(true);
        company = Company.builder()
                .id(1L).name("Clínica Test").allowClienteBooking(true).build();

        adminUser = User.builder()
                .id(1L).name("Admin").email("admin@test.com")
                .role(Role.ADMIN).active(true).company(company).build();

        clientUser = User.builder()
                .id(2L).name("Cliente").email("client@test.com")
                .role(Role.CLIENT).active(true).build();

        when(userRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(adminUser));
        when(userRepository.findByEmail("client@test.com")).thenReturn(Optional.of(clientUser));
    }

    // ── POST /appointments ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void createAppointment_asClient_returns200WithPendingResponse() throws Exception {
        CreateAppointmentRequest req = buildCreateRequest();
        AppointmentResponse      res = buildResponse(AppointmentStatus.PENDING);

        when(appointmentService.create(any(), any())).thenReturn(res);

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void createAppointment_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest()))
                        .with(csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void createAppointment_withConflict_returns400() throws Exception {
        when(appointmentService.create(any(), any()))
                .thenThrow(new BusinessException("Horário indisponível."));

        mockMvc.perform(post("/api/v1/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildCreateRequest()))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ── POST /appointments/public ────────────────────────────────────────────

    @Test
    void createPublicAppointment_noAuth_returns200() throws Exception {
        AppointmentResponse res = buildResponse(AppointmentStatus.PENDING);
        when(appointmentService.createPublic(any())).thenReturn(res);

        String body = objectMapper.writeValueAsString(buildPublicRequest());

        mockMvc.perform(post("/api/v1/appointments/public")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── GET /appointments/{id} ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void findById_existingAppointment_returns200() throws Exception {
        when(appointmentService.findById(10L)).thenReturn(buildResponse(AppointmentStatus.CONFIRMED));

        mockMvc.perform(get("/api/v1/appointments/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    // ── GET /appointments ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void myAppointments_authenticated_returns200PagedResponse() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(List.of(buildResponse(AppointmentStatus.PENDING)));
        when(appointmentService.findByCurrentUser(anyLong(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/appointments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(10));
    }

    // ── GET /appointments/company/{id} ───────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void byCompany_asAdmin_returns200() throws Exception {
        Page<AppointmentResponse> page = new PageImpl<>(List.of(buildResponse(AppointmentStatus.CONFIRMED)));
        when(appointmentService.findByCompany(anyLong(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/appointments/company/1")
                        .param("start", "2026-06-01T00:00:00")
                        .param("end", "2026-06-30T23:59:59"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void byCompany_asClient_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/appointments/company/1")
                        .param("start", "2026-06-01T00:00:00")
                        .param("end", "2026-06-30T23:59:59"))
                .andExpect(status().isForbidden());
    }

    // ── GET /appointments/company/{id}/pending ───────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void pendingByCompany_asAdmin_returns200() throws Exception {
        when(appointmentService.findPendingByCompany(anyLong(), any()))
                .thenReturn(List.of(buildResponse(AppointmentStatus.PENDING)));

        mockMvc.perform(get("/api/v1/appointments/company/1/pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"));
    }

    // ── PUT /appointments/{id}/confirm ───────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void confirm_asAdmin_returns200() throws Exception {
        when(appointmentService.confirm(anyLong(), any()))
                .thenReturn(buildResponse(AppointmentStatus.CONFIRMED));

        mockMvc.perform(put("/api/v1/appointments/10/confirm").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void confirm_asClient_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/appointments/10/confirm").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── PUT /appointments/{id}/cancel ────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void cancel_asAdmin_returns200() throws Exception {
        when(appointmentService.cancel(anyLong(), any(), any()))
                .thenReturn(buildResponse(AppointmentStatus.CANCELLED));

        mockMvc.perform(put("/api/v1/appointments/10/cancel")
                        .param("reason", "Agenda lotada")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    // ── PUT /appointments/{id}/complete ──────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void complete_asAdmin_returns200() throws Exception {
        when(appointmentService.complete(anyLong(), any()))
                .thenReturn(buildResponse(AppointmentStatus.COMPLETED));

        mockMvc.perform(put("/api/v1/appointments/10/complete").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser(username = "client@test.com", roles = {"CLIENT"})
    void complete_asClient_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/appointments/10/complete").with(csrf()))
                .andExpect(status().isForbidden());
    }

    // ── PUT /appointments/{id}/reschedule ────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = {"ADMIN"})
    void reschedule_asAdmin_returns200() throws Exception {
        RescheduleRequest req = new RescheduleRequest();
        req.setNewStartAt(LocalDateTime.now().plusDays(3));
        req.setNewEndAt(LocalDateTime.now().plusDays(3).plusHours(1));

        when(appointmentService.reschedule(anyLong(), any(), any()))
                .thenReturn(buildResponse(AppointmentStatus.PENDING));

        mockMvc.perform(put("/api/v1/appointments/10/reschedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private AppointmentResponse buildResponse(AppointmentStatus status) {
        return AppointmentResponse.builder()
                .id(10L).companyId(1L).companyName("Clínica Test")
                .clientId(2L).clientName("Cliente")
                .professionalId(3L).professionalName("Dr. Smith")
                .startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(1))
                .status(status)
                .build();
    }

    private CreateAppointmentRequest buildCreateRequest() {
        CreateAppointmentRequest req = new CreateAppointmentRequest();
        req.setCompanyId(1L);
        req.setProfessionalId(3L);
        req.setStartAt(LocalDateTime.now().plusDays(1));
        req.setEndAt(LocalDateTime.now().plusDays(1).plusHours(1));
        return req;
    }

    private com.scheduling.api.appointment.dto.PublicAppointmentRequest buildPublicRequest() {
        com.scheduling.api.appointment.dto.PublicAppointmentRequest req =
                new com.scheduling.api.appointment.dto.PublicAppointmentRequest();
        req.setCompanyId(1L);
        req.setProfessionalId(3L);
        req.setStartAt(LocalDateTime.now().plusDays(1));
        req.setEndAt(LocalDateTime.now().plusDays(1).plusHours(1));
        req.setClientName("José");
        req.setClientEmail("jose@test.com");
        return req;
    }
}
