package com.scheduling.api.appointment.controller;

import com.scheduling.api.appointment.dto.AppointmentResponse;
import com.scheduling.api.appointment.dto.CalendarDayResponse;
import com.scheduling.api.appointment.dto.CreateAppointmentRequest;
import com.scheduling.api.appointment.dto.RescheduleRequest;
import com.scheduling.api.appointment.service.AppointmentService;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @PostMapping
    @Operation(summary = "Cria agendamento - CLIENT entra como PENDING, ADMIN/MANAGER como CONFIRMED")
    public ResponseEntity<AppointmentResponse> create(
            @RequestBody @Valid CreateAppointmentRequest req,
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername())
                .orElseThrow();
        return ResponseEntity.ok(appointmentService.create(req, currentUser));
    }

    @GetMapping
    @Operation(summary = "Lista agendamentos do usuário logado")
    public ResponseEntity<List<AppointmentResponse>> myAppointments(
            @AuthenticationPrincipal UserDetails principal) {
        User currentUser = userRepository.findByEmail(principal.getUsername())
                .orElseThrow();
        return ResponseEntity.ok(appointmentService.findByCurrentUser(currentUser.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um agendamento")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Confirma agendamento PENDING")
    public ResponseEntity<AppointmentResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.confirm(id));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancela agendamento")
    public ResponseEntity<AppointmentResponse> cancel(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(appointmentService.cancel(id, reason));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Remarca para novo horário")
    public ResponseEntity<AppointmentResponse> reschedule(
            @PathVariable Long id,
            @RequestBody @Valid RescheduleRequest req) {
        return ResponseEntity.ok(appointmentService.reschedule(id, req));
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Agenda completo da empresa por período")
    public ResponseEntity<List<AppointmentResponse>> byCompany(
            @PathVariable Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return ResponseEntity.ok(appointmentService.findByCompany(companyId, start, end));
    }

    @GetMapping("/company/{companyId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Fila de solicitações pendentes")
    public ResponseEntity<List<AppointmentResponse>> pending(@PathVariable Long companyId) {
        return ResponseEntity.ok(appointmentService.findPendingByCompany(companyId));
    }

    @GetMapping("/calendar")
    @Operation(summary = "Resumo do mês para pintar o calendário")
    public ResponseEntity<List<CalendarDayResponse>> calendar(
            @RequestParam Long companyId,
            @RequestParam String month) {
        return ResponseEntity.ok(appointmentService.getCalendarMonth(companyId, YearMonth.parse(month)));
    }
}