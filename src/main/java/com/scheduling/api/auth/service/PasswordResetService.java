package com.scheduling.api.auth.service;

import com.scheduling.api.exception.BusinessException;
import com.scheduling.api.mail.MailService;
import com.scheduling.api.user.model.User;
import com.scheduling.api.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Gerencia o fluxo de recuperação de senha via token temporário no Redis.
 * TTL do token: 1 hora.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository      userRepository;
    private final PasswordEncoder     passwordEncoder;
    private final MailService         mailService;

    private static final long   TTL_SECONDS = 3600L;
    private static final String KEY_PREFIX  = "pwd_reset:";

    /**
     * Gera token UUID, salva no Redis com TTL 1h e dispara e-mail.
     * Se o e-mail não existir na base, retorna silenciosamente
     * (não revelar existência de conta).
     */
    public void requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            String token = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + token,
                    email,
                    TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            mailService.sendPasswordResetEmail(email, user.getName(), token);
            log.info("[PasswordReset] Token gerado para {}", email);
        });
    }

    /**
     * Valida token, atualiza senha e apaga o token do Redis (uso único).
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String key   = KEY_PREFIX + token;
        String email = redisTemplate.opsForValue().get(key);

        if (email == null) {
            throw new BusinessException("Link inválido ou expirado. Solicite um novo.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("Usuário não encontrado."));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redisTemplate.delete(key);

        log.info("[PasswordReset] Senha redefinida para {}", email);
    }
}
