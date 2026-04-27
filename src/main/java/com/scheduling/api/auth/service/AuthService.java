package com.scheduling.api.auth.service;

import com.scheduling.api.auth.dto.LoginRequest;
import com.scheduling.api.auth.dto.RefreshTokenRequest;
import com.scheduling.api.auth.dto.RegisterRequest;
import com.scheduling.api.auth.dto.TokenResponse;
import com.scheduling.api.auth.model.RefreshToken;
import com.scheduling.api.auth.repository.RefreshTokenRepository;
import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpirarionMs;

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if(userRepository.existsByEmail(req.getEmail())) {
            throw new BusinessException("Email já cadastrado");
        }
        User user =User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(Role.CLIENT)
                .active(true)
                .build();
        userRepository.save(user);
        return buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        User user= userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new BusinessException("Credenciais inválidas"));
        return  buildTokenResponse(user);
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest req) {
        RefreshToken token =refreshTokenRepository.findByToken(req.getRefreshToken())
                .orElseThrow();
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())){
            throw new BusinessException("Refresh token expirado ou revogado");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        return buildTokenResponse(token.getUser());
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email)
                .ifPresent(refreshTokenRepository::deleteByUser);
    }

    private TokenResponse buildTokenResponse(User user) {
            refreshTokenRepository.deleteByUser(user);
            String rawRefresh = UUID.randomUUID().toString();
            refreshTokenRepository.save(RefreshToken.builder()
                    .token(rawRefresh).user(user)
                    .expiresAt(Instant.now().plusMillis(refreshExpirarionMs))
                    .revoked(false).build());
            return TokenResponse.builder()
                    .accessToken(tokenService.generateToken(user))
                    .refreshToken(rawRefresh)
                    .tokenType("Bearer")
                    .expiresIn(refreshExpirarionMs / 1000)
                    .build();
    }

}
