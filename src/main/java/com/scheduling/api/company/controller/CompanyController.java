package com.scheduling.api.company.controller;

import com.scheduling.api.company.dto.CompanyRequest;
import com.scheduling.api.company.dto.CompanyResponse;
import com.scheduling.api.company.dto.CompanySettingsRequest;
import com.scheduling.api.company.service.CompanyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Tag(name = "Empresas")
public class CompanyController {

    private final CompanyService companyService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria empresa (Somente ADMIN)")
    public ResponseEntity<CompanyResponse> create(@RequestBody @Valid CompanyRequest req) {
        return  ResponseEntity.ok(companyService.create(req));
    }

    @GetMapping
    @Operation(summary = "Lista todas as empresas ativas")
    public ResponseEntity<List<CompanyResponse>> findAll() {
        return ResponseEntity.ok(companyService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalha uma empresa")
    public ResponseEntity<CompanyResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(companyService.findById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Atualiza dados da empresa")
    public ResponseEntity<CompanyResponse> update(@PathVariable Long id,
                                                  @RequestBody @Valid CompanyRequest req) {
        return ResponseEntity.ok(companyService.update(id, req));
    }

    @PutMapping("/{id}/settings")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Altera configurações: Ativar/desativar agendamento publico, suspender empresa")
    public ResponseEntity<CompanyResponse> updateSettings(@PathVariable Long id,
                                                          @RequestBody @Valid CompanySettingsRequest req) {
        return ResponseEntity.ok(companyService.updateSettings(id, req));
    }

}
