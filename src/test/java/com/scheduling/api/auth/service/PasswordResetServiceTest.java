package com.scheduling.api.auth.service;

import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.mail.MailService;
import com.scheduling.api.user.model.Role;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // opsForValue() configurado em @BeforeEach
class PasswordResetServiceTest {

    @Mock private StringRedisTemplate       redisTemplate;
    @Mock private UserRepository            userRepository;
    @Mock private PasswordEncoder           passwordEncoder;
    @Mock private MailService               mailService;
    @Mock private ValueOperations<String, String> valueOps;

    @InjectMocks
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── requestReset() ────────────────────────────────────────────────────────

    @Test
    void requestReset_existingEmail_savesTokenInRedisAndSendsMail() {
        User user = User.builder()
                .id(1L).name("João").email("joao@test.com")
                .role(Role.CLIENT).build();

        when(userRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(user));

        service.requestReset("joao@test.com");

        // Token UUID salvo com TTL de 1h (3600 s)
        verify(valueOps).set(
                startsWith("pwd_reset:"),
                eq("joao@test.com"),
                eq(3600L),
                eq(TimeUnit.SECONDS)
        );
        // E-mail enviado com os dados corretos
        verify(mailService).sendPasswordResetEmail(
                eq("joao@test.com"),
                eq("João"),
                argThat(token -> token != null && !token.isBlank())
        );
    }

    @Test
    void requestReset_nonExistingEmail_doesNothingSilently() {
        when(userRepository.findByEmail("nobody@test.com")).thenReturn(Optional.empty());

        assertThatNoException().isThrownBy(() -> service.requestReset("nobody@test.com"));

        verifyNoInteractions(mailService);
        verify(valueOps, never()).set(any(), any(), anyLong(), any());
    }

    // ── resetPassword() ───────────────────────────────────────────────────────

    @Test
    void resetPassword_validToken_updatesPasswordAndDeletesRedisKey() {
        String token = "valid-uuid-token-123";
        String key   = "pwd_reset:" + token;

        User user = User.builder()
                .id(1L).email("joao@test.com").name("João")
                .password("oldHash").role(Role.CLIENT).build();

        when(valueOps.get(key)).thenReturn("joao@test.com");
        when(userRepository.findByEmail("joao@test.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("novasenha123")).thenReturn("newHash");

        service.resetPassword(token, "novasenha123");

        assertThat(user.getPassword()).isEqualTo("newHash");
        verify(userRepository).save(user);
        verify(redisTemplate).delete(key);
    }

    @Test
    void resetPassword_invalidToken_throwsBusinessException() {
        String token = "invalid-or-expired";
        when(valueOps.get("pwd_reset:" + token)).thenReturn(null);

        assertThatThrownBy(() -> service.resetPassword(token, "newPassword"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválido ou expirado");

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void resetPassword_validTokenButUserDeleted_throwsBusinessException() {
        String token = "valid-token-orphan";
        String key   = "pwd_reset:" + token;

        when(valueOps.get(key)).thenReturn("deleted@test.com");
        when(userRepository.findByEmail("deleted@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword(token, "newPassword"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("não encontrado");
    }
}
