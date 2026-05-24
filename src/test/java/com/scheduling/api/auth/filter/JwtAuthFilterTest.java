package com.scheduling.api.auth.filter;

import com.scheduling.api.auth.service.TokenBlacklistService;
import com.scheduling.api.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock private TokenService         tokenService;
    @Mock private TokenBlacklistService tokenBlacklistService;
    @Mock private UserDetailsService   userDetailsService;
    @Mock private FilterChain          chain;

    private JwtAuthFilter filter;

    private MockHttpServletRequest  request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter   = new JwtAuthFilter(tokenService, tokenBlacklistService, userDetailsService);
        request  = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ── Bearer header ─────────────────────────────────────────────────────────

    @Test
    void validBearerToken_setsAuthenticationInContext() throws Exception {
        String token = "valid.jwt.token";
        UserDetails details = buildUserDetails("user@test.com", "ROLE_CLIENT");

        request.addHeader("Authorization", "Bearer " + token);
        request.setServletPath("/api/v1/some-endpoint");

        when(tokenService.isValid(token)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenService.extractEmail(token)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(details);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@test.com");
        verify(chain).doFilter(request, response);
    }

    @Test
    void expiredToken_doesNotAuthenticate() throws Exception {
        String token = "expired.jwt.token";

        request.addHeader("Authorization", "Bearer " + token);
        request.setServletPath("/api/v1/some-endpoint");

        when(tokenService.isValid(token)).thenReturn(false);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void blacklistedToken_doesNotAuthenticate() throws Exception {
        String token = "blacklisted.jwt.token";

        request.addHeader("Authorization", "Bearer " + token);
        request.setServletPath("/api/v1/some-endpoint");

        when(tokenService.isValid(token)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(userDetailsService, never()).loadUserByUsername(any());
        verify(chain).doFilter(request, response);
    }

    @Test
    void noToken_passesChainWithoutAuthentication() throws Exception {
        request.setServletPath("/api/v1/auth/login");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService, tokenBlacklistService, userDetailsService);
        verify(chain).doFilter(request, response);
    }

    // ── SSE endpoint — ?token= ────────────────────────────────────────────────

    @Test
    void sseEndpoint_withQueryToken_authenticates() throws Exception {
        String token = "sse.query.token";
        UserDetails details = buildUserDetails("user@test.com", "ROLE_CLIENT");

        request.setServletPath("/api/v1/notifications/subscribe");
        request.setParameter("token", token);

        when(tokenService.isValid(token)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(token)).thenReturn(false);
        when(tokenService.extractEmail(token)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(details);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName())
                .isEqualTo("user@test.com");
    }

    @Test
    void sseEndpoint_queryTokenTakesPriorityOverBearerHeader() throws Exception {
        String queryToken  = "sse.query.token";
        String bearerToken = "bearer.token.should.be.ignored";
        UserDetails details = buildUserDetails("user@test.com", "ROLE_CLIENT");

        request.setServletPath("/api/v1/notifications/subscribe");
        request.setParameter("token", queryToken);
        request.addHeader("Authorization", "Bearer " + bearerToken);

        when(tokenService.isValid(queryToken)).thenReturn(true);
        when(tokenBlacklistService.isBlacklisted(queryToken)).thenReturn(false);
        when(tokenService.extractEmail(queryToken)).thenReturn("user@test.com");
        when(userDetailsService.loadUserByUsername("user@test.com")).thenReturn(details);

        filter.doFilter(request, response, chain);

        // query token foi usado; bearer token nunca foi validado
        verify(tokenService).isValid(queryToken);
        verify(tokenService, never()).isValid(bearerToken);
    }

    @Test
    void nonSseEndpoint_ignoresQueryParamToken() throws Exception {
        // ?token= só é aceito no path SSE
        request.setServletPath("/api/v1/appointments");
        request.setParameter("token", "should.be.ignored");

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(tokenService);
    }

    // ── helper ───────────────────────────────────────────────────────────────

    private UserDetails buildUserDetails(String username, String... roles) {
        return User.withUsername(username)
                .password("hashed")
                .authorities(roles)
                .build();
    }
}
