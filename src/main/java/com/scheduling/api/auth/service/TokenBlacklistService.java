package com.scheduling.api.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Gerencia a blacklist de access tokens no Redis.
 *
 * <p>Quando um usuário faz logout, o access token atual é adicionado aqui
 * com TTL igual ao tempo restante de validade. O {@code JwtAuthFilter} consulta
 * este serviço antes de autenticar qualquer requisição.</p>
 */
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final StringRedisTemplate redisTemplate;

    private static final String PREFIX = "blacklist:";

    /**
     * Adiciona o token à blacklist com o TTL informado.
     *
     * @param token      access token JWT bruto (string completa)
     * @param ttlSeconds segundos restantes de validade do token
     */
    public void blacklist(String token, long ttlSeconds) {
        if (ttlSeconds > 0) {
            redisTemplate.opsForValue()
                    .set(PREFIX + token, "1", ttlSeconds, TimeUnit.SECONDS);
        }
    }

    /**
     * Verifica se o token está na blacklist (foi revogado via logout).
     *
     * @param token access token JWT bruto
     * @return {@code true} se o token foi revogado
     */
    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
