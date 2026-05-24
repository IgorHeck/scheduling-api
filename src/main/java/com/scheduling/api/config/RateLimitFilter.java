package com.scheduling.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Rate limiter para endpoints públicos sensíveis.
 * Usa Redis INCR + EXPIRE para contar requisições por IP em uma janela deslizante.
 * Como @Component, o Spring Boot registra automaticamente como servlet filter —
 * executa antes do Spring Security, sem necessidade de addFilterBefore().
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    /**
     * Mapa de endpoint → [máx. requisições, janela em segundos].
     * Apenas métodos POST são limitados.
     */
    private static final Map<String, int[]> LIMITS = Map.of(
        "/api/v1/auth/register",       new int[]{ 5, 60},   // 5 req/min por IP
        "/api/v1/appointments/public", new int[]{10, 60}    // 10 req/min por IP
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        // Só intercepta POST nos endpoints configurados
        return !LIMITS.containsKey(req.getRequestURI())
            || !"POST".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        String path      = req.getRequestURI();
        int[]  limit     = LIMITS.get(path);
        String ip        = resolveClientIp(req);
        String key       = "rl:" + path.replace('/', '_') + ":" + ip;
        int    maxReqs   = limit[0];
        int    windowSec = limit[1];

        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, windowSec, TimeUnit.SECONDS);
        }

        if (count != null && count > maxReqs) {
            log.warn("[RateLimit] IP={} bloqueado em {} ({} req/{}s)", ip, path, count, windowSec);
            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(
                "{\"error\":\"Muitas requisições. Aguarde antes de tentar novamente.\"}"
            );
            return;
        }

        chain.doFilter(req, res);
    }

    private String resolveClientIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }
}
