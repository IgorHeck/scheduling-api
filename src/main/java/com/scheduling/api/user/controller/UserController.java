package com.scheduling.api.user.controller;

import com.scheduling.api.user.dto.AssignCompanyRequest;
import com.scheduling.api.user.dto.CreateManagerRequest;
import com.scheduling.api.user.dto.UpdateUserRequest;
import com.scheduling.api.user.dto.UserResponse;
import com.scheduling.api.user.repository.UserRepository;
import com.scheduling.api.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Usuários")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/me")
    @Operation(summary = "Retorna o perfil do usuário logado")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UserDetails principal) {
        // o Spring injeta o UserDetails com o email (username) extraído do JWT
        return userRepository.findByEmail(principal.getUsername())
                .map(u -> ResponseEntity.ok(userService.findById(u.getId())))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/me")
    @Operation(summary = "Atualiza dados do próprio perfil")
    public ResponseEntity<UserResponse> updateMe(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody @Valid UpdateUserRequest req) {
        return userRepository.findByEmail(principal.getUsername())
                .map(u -> ResponseEntity.ok(userService.update(u.getId(), req)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cria usuário com role MANAGER (somente ADMIN)")
    public ResponseEntity<UserResponse> createManager(@RequestBody @Valid CreateManagerRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createManager(req));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista todos os usuários (somente ADMIN)")
    public ResponseEntity<List<UserResponse>> findAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca usuário por ID")
    public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @PutMapping("/{id}/company")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vincula ou desvincula um usuário a uma empresa (somente ADMIN)")
    public ResponseEntity<UserResponse> assignCompany(@PathVariable Long id,
                                                      @RequestBody AssignCompanyRequest req) {
        return ResponseEntity.ok(userService.assignCompany(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desativa usuário (soft delete)")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        userService.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}