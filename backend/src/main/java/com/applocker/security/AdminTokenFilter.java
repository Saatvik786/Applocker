package com.applocker.security;

import com.applocker.config.ApplockerProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private final ApplockerProperties properties;

    public AdminTokenFilter(ApplockerProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/enrollment-tokens")
                && !path.startsWith("/api/v1/policies")
                && !path.startsWith("/api/v1/applocker")
                && !path.equals("/api/v1/devices")
                && !path.startsWith("/api/v1/devices/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!authorized(request.getHeader("Authorization"))) {
            byte[] body = "{\"error\":\"Unauthorized\"}".getBytes(StandardCharsets.UTF_8);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean authorized(String header) {
        String expected = properties.adminToken();
        if (expected == null || expected.isBlank() || header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return false;
        }
        String presented = header.substring(7).trim();
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                presented.getBytes(StandardCharsets.UTF_8));
    }
}
