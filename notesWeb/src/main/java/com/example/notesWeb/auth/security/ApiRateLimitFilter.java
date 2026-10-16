package com.example.notesWeb.auth.security;

import com.example.notesWeb.auth.contract.AuthResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class ApiRateLimitFilter extends OncePerRequestFilter {
    private final ApiRateLimiter limiter;

    public ApiRateLimitFilter(ApiRateLimiter limiter) { this.limiter = limiter; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getMethod().equals("OPTIONS") || request.getServletPath().startsWith("/actuator/health");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getServletPath();
        String ip = limiter.client(request);
        try {
            if (request.getMethod().equals("POST") && path.equals("/api/auth/register")) {
                limiter.check("register-ip", ip, 20, 3600);
            } else if (request.getMethod().equals("POST") && path.equals("/api/auth/login")) {
                limiter.check("login-ip", ip, 60, 60);
            } else {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                String identity = auth != null && auth.getPrincipal() instanceof AuthResponse user
                        ? "user:"+user.getId() : "ip:"+ip;
                // Polling, refresh and ordinary API reads share a generous, separate budget.
                limiter.check("api", identity, 300, 60);
            }
        } catch (ErrorResponseException error) {
            response.setStatus(error.getStatusCode().value());
            error.getHeaders().forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
            response.setContentType("application/json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write(error.getStatusCode().value() == 429
                    ? "{\"error\":\"rate_limited\"}" : "{\"error\":\"temporarily_unavailable\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
