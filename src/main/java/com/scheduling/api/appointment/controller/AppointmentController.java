package com.scheduling.api.appointment.controller;

import com.scheduling.api.appointment.dto.AppointmentResponse;
import com.scheduling.api.appointment.dto.CalendarDayResponse;
import com.scheduling.api.appointment.dto.CreateAppointmentRequest;
import com.scheduling.api.appointment.dto.RescheduleRequest;
import com.scheduling.api.appointment.service.AppointmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @PostMapping
    @Operation(summary = "Cria agendamento - CLIENT entra como PENDING, ADMIN,MANAGER entra como CONFIRMED")
    public ResponseEntity<AppointmentResponse> create(@RequestBody @Valid CreateAppointmentRequest req) {
        return ResponseEntity.ok(null);
    }

    @GetMapping
    @Operation(summary = "Lista agendamentos do usuário logado")
    public ResponseEntity<List<AppointmentResponse>> myAppointments() {
        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha um agendamento")
    public ResponseEntity<AppointmentResponse> findById(@PathVariable long id) {
        return ResponseEntity.ok(appointmentService.findById(id));
    }

    @PutMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Confirme agendamento PENDING (dono, manager)")
    public ResponseEntity<AppointmentResponse> confirm(@PathVariable Long id) {
        return ResponseEntity.ok(appointmentService.confirm(id));
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancela agendamento - qualquer parte pode cancelar o próprio")
    public ResponseEntity<AppointmentResponse> cancel(@PathVariable Long id,
                                                      @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(appointmentService.cancel(id, reason));
    }

    @PutMapping("/{id}/reschedule")
    @Operation(summary = "Remarca para novo horário - volta para PEDING")
    public ResponseEntity<AppointmentResponse> reschedule(@PathVariable Long id,
                                                          @RequestBody @Valid RescheduleRequest req) {
        return ResponseEntity.ok(appointmentService.reschedule(id, req));
    }

    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Agenda completo da empresa por período (painel do gestor)")
    public ResponseEntity<List<AppointmentResponse>> byCompany(@PathVariable Long companyId,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDateTime start,
                                                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)LocalDateTime end) {
        return ResponseEntity.ok(appointmentService.findByCompany(companyId, start, end));
    }

    @GetMapping("/company/{companyId}/pending")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Fila de solicitações pendentes - para o painel do dono confirmar")
    public ResponseEntity<List<AppointmentResponse>> pending(@PathVariable Long companyId) {
        return ResponseEntity.ok(appointmentService.findPendingByCompany(companyId));
    }

    @GetMapping("/calendar")
    @Operation(summary = "Resumo do mês para pintar o calendário (dias ocupados/livres)")
    public ResponseEntity<List<CalendarDayResponse>> calendar(@RequestParam Long companyId,
                                                              @RequestParam String month) {
        return ResponseEntity.ok(appointmentService.getCalendarMonth(companyId, YearMonth.parse(month)));
    }
}
