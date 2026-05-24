package com.scheduling.api.appointment.controller;

import com.scheduling.api.appointment.dto.*;
import com.scheduling.api.appointment.service.AppointmentService;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Agendamentos")
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final UserRepository userRepository;

    // ── Criação ──────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Cria agendamento — CLIENT → PENDING, ADMIN/MANAGER → CONFIRMED")
    public ResponseEntity<AppointmentResponse> create(
            @RequestBody @Valid CreateAppointmentRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.create(req, currentUser));
    }

    @PostMapping("/public")
    @Operation(summary = "Solicitação pública — sem autenticação")
    public ResponseEntity<AppointmentResponse> createPublic(
            @RequestBody @Valid PublicAppointmentRequest req) {
        return ResponseEntity.ok(appointmentService.createPublic(req));
    }

    // ── Consultas ────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Lista agendamentos do usuário logado (paginado)")
    public ResponseEntity<Page<AppointmentResponse>> myAppointments(
            @AuthenticationPrincipal UserDetails principal,
            @PageableDefault(size = 10, sort = "startAt") Pageable pageable) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.findByCurrentUser(currentUser.getId(), pageable));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um agendamento")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Agenda da empresa por período (paginado)")
    public ResponseEntity<Page<AppointmentResponse>> byCompany(
            @PathVariable Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @PageableDefault(size = 20, sort = "startAt") Pageable pageable,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.findByCompany(companyId, start, end, pageable, currentUser));
    }

    @GetMapping("/company/{companyId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Fila de solicitações pendentes")
    public ResponseEntity<List<AppointmentResponse>> pending(
            @PathVariable Long companyId,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.findPendingByCompany(companyId, currentUser));
    }

    @GetMapping("/calendar")
    @Operation(summary = "Resumo do mês para o calendário")
    public ResponseEntity<List<CalendarDayResponse>> calendar(
            @RequestParam Long companyId,
            @RequestParam String month) {
        return ResponseEntity.ok(appointmentService.getCalendarMonth(companyId, YearMonth.parse(month)));
    }

    // ── Transições de status ─────────────────────────────────────────────────

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Confirma agendamento PENDING")
    public ResponseEntity<AppointmentResponse> confirm(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.confirm(id, currentUser));
    }

    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Marca agendamento CONFIRMED como COMPLETED")
    public ResponseEntity<AppointmentResponse> complete(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.complete(id, currentUser));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancela agendamento")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.cancel(id, reason, currentUser));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Remarca para novo horário")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @RequestBody @Valid RescheduleRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = resolveUser(principal);
        return ResponseEntity.ok(appointmentService.reschedule(id, req, currentUser));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    private User resolveUser(UserDetails principal) {
        return userRepository.findByEmail(principal.getUsername()).orElseThrow();
    }
}
