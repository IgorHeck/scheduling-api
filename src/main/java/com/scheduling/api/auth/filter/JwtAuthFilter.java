package com.scheduling.api.auth.filter;

import com.scheduling.api.auth.service.TokenBlacklistService;
import com.scheduling.api.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Sem @Component — instância criada manualmente em SecurityConfig para controlar as dependências
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String SSE_PATH = "/api/v1/notifications/subscribe";

    private final TokenService tokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String token = extractToken(req);

        if (token != null && tokenService.isValid(token) && !tokenBlacklistService.isBlacklisted(token)) {
            String email = tokenService.extractEmail(token);
            UserDetails user = userDetailsService.loadUserByUsername(email);
            var auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        chain.doFilter(req, res);
    }

    /**
     * Extrai o JWT de duas fontes possíveis:
     * 1. Header "Authorization: Bearer {token}" — para todas as rotas normais.
     * 2. Query param "?token={token}" — apenas para o endpoint SSE, porque
     *    EventSource (browser) não suporta headers customizados.
     */
    private String extractToken(HttpServletRequest req) {
        // Query param — exclusivo para SSE
        if (SSE_PATH.equals(req.getServletPath())) {
            String queryToken = req.getParameter("token");
            if (queryToken != null && !queryToken.isBlank()) {
                return queryToken;
            }
        }

        // Header padrão Bearer
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }

        return null;
    }
}
