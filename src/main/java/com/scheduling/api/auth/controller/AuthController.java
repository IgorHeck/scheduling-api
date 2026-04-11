package com.scheduling.api.auth.controller;

import com.scheduling.api.auth.dto.LoginRequest;
import com.scheduling.api.auth.dto.RefreshTokenRequest;
import com.scheduling.api.auth.dto.RegisterRequest;
import com.scheduling.api.auth.dto.TokenResponse;
import com.scheduling.api.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registro, login e renovação de token")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Cadastra novo usuário (publico)")
    public ResponseEntity<TokenResponse> register(@RequestBody @Valid RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e retorna JWT + refresh token")
    public ResponseEntity<TokenResponse> login(@RequestBody @Valid LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renova o access token usando refresh token")
    public ResponseEntity<TokenResponse> refresh(@RequestBody @Valid RefreshTokenRequest req) {
        return ResponseEntity.ok(authService.refresh(req));
    }

    @PostMapping("/logout")
    @Operation(summary = "Invalida o access token usando o refresh token")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UserDetails principal) {
        authService.logout(principal.getUsername());
        return ResponseEntity.noContent().build();
    }


}
