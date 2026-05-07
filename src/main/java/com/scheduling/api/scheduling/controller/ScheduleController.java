package com.scheduling.api.scheduling.controller;

import com.scheduling.api.scheduling.dto.AvailableSlotResponse;
import com.scheduling.api.scheduling.dto.ScheduleBlockRequest;
import com.scheduling.api.scheduling.dto.ScheduleRequest;
import com.scheduling.api.scheduling.model.Schedule;
import com.scheduling.api.scheduling.model.ScheduleBlock;
import com.scheduling.api.scheduling.repository.ScheduleBlockRepository;
import com.scheduling.api.scheduling.repository.ScheduleRepository;
import com.scheduling.api.scheduling.service.AvaliabilityService;
import com.scheduling.api.scheduling.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/schedules")
@RequiredArgsConstructor
@Tag(name = "Horários e disponibilidade")
public class ScheduleController {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleBlockRepository blockRepository;
    private final AvaliabilityService avaliabilityService;
    private final ScheduleService scheduleService;

    @GetMapping("/available")
    @Operation(summary = "Retorna os slots disponíveis de um dia")
    public ResponseEntity<List<AvailableSlotResponse>> getAvaliable(
            @RequestParam Long companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(avaliabilityService.getAvailableSlots(companyId, date));
    }

    @GetMapping("/company/{companyId}")
    @Operation(summary = "Lista a grade de horários da empresa")
    public ResponseEntity<List<Schedule>> getByCompany(@PathVariable Long companyId) {
        return ResponseEntity.ok(scheduleRepository.findByCompanyIdAndActiveTrue(companyId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Cria uma grade de horários para um profissional")
    public ResponseEntity<Schedule> create(@RequestBody @Valid ScheduleRequest req) {
        return ResponseEntity.ok(scheduleService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Edita uma grade de horários")
    public ResponseEntity<Schedule> update(@PathVariable Long id,
                                           @RequestBody @Valid ScheduleRequest req) {
        return ResponseEntity.ok(scheduleService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Remove uma grade de horários")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        scheduleService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/blocks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Bloqueia um dia ou faixa de horário")
    public ResponseEntity<ScheduleBlock> createBlock(@RequestBody ScheduleBlockRequest req) {
        return ResponseEntity.ok(scheduleService.createBlock(req));
    }

    @GetMapping("/blocks")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Lista bloqueios ativos")
    public ResponseEntity<List<ScheduleBlock>> listBlocks(@RequestParam Long companyId) {
        return ResponseEntity.ok(blockRepository.findByCompanyId(companyId));
    }

    @DeleteMapping("/blocks/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Remove um bloqueio de horário")
    public ResponseEntity<Void> deleteBlock(@PathVariable Long id) {
        blockRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}