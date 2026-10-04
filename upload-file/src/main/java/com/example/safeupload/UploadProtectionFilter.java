package com.example.safeupload;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UploadProtectionFilter extends OncePerRequestFilter {
    private static final int MAX_UPLOADS_PER_MINUTE = 20;
    private final byte[] expectedToken;
    private final Deque<Long> uploadTimes = new ArrayDeque<>();

    public UploadProtectionFilter(@Value("${app.upload.api-token}") String apiToken) {
        this.expectedToken = apiToken.getBytes(StandardCharsets.UTF_8);
        if (expectedToken.length < 32) {
            throw new IllegalArgumentException("UPLOAD_API_TOKEN must be at least 32 bytes");
        }
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals("/upload") && !path.startsWith("/uploads/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String prefix = "Bearer ";
        byte[] suppliedToken = authorization != null && authorization.startsWith(prefix)
                ? authorization.substring(prefix.length()).getBytes(StandardCharsets.UTF_8)
                : new byte[0];
        if (!MessageDigest.isEqual(expectedToken, suppliedToken)) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        if ("POST".equals(request.getMethod()) && !allowUpload()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            response.sendError(429);
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized boolean allowUpload() {
        long now = System.nanoTime();
        long cutoff = now - TimeUnit.MINUTES.toNanos(1);
        while (!uploadTimes.isEmpty() && uploadTimes.peekFirst() <= cutoff) {
            uploadTimes.removeFirst();
        }
        // ponytail: process-local global quota; use a shared per-user limiter for multi-instance deployments.
        if (uploadTimes.size() >= MAX_UPLOADS_PER_MINUTE) {
            return false;
        }
        uploadTimes.addLast(now);
        return true;
    }
}
