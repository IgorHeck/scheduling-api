package com.scheduling.api.auth.controller;

import com.scheduling.api.auth.dto.*;
import com.scheduling.api.auth.service.AuthService;
import com.scheduling.api.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Registro, login, renovação de token e recuperação de senha")
public class AuthController {

    private final AuthService          authService;
    private final PasswordResetService passwordResetService;

    @PostMapping("/register")
    @Operation(summary = "Cadastra novo usuário (público)")
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
    @Operation(summary = "Revoga os refresh tokens e adiciona o access token à blacklist")
    public ResponseEntity<Void> logout(HttpServletRequest request,
                                       @AuthenticationPrincipal UserDetails principal) {
        if (principal == null) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.UNAUTHORIZED).build();
        }
        String header   = request.getHeader("Authorization");
        String rawToken = (header != null && header.startsWith("Bearer "))
                ? header.substring(7) : null;
        authService.logout(principal.getUsername(), rawToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Envia e-mail de recuperação de senha (público)")
    public ResponseEntity<Void> forgotPassword(@RequestBody @Valid ForgotPasswordRequest req) {
        // Sempre retorna 204 — não revelamos se o e-mail existe na base
        passwordResetService.requestReset(req.getEmail());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Redefine a senha usando o token enviado por e-mail (público)")
    public ResponseEntity<Void> resetPassword(@RequestBody @Valid ResetPasswordRequest req) {
        passwordResetService.resetPassword(req.getToken(), req.getNewPassword());
        return ResponseEntity.noContent().build();
    }
}
