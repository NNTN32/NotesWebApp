package com.example.notesWeb.security;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
public class ApiRateLimiter {
    // Redis time and one atomic bucket keep limits consistent across application replicas.
    static final DefaultRedisScript<Long> BUCKET = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = time[1] * 1000 + math.floor(time[2] / 1000)
            local capacity, window = tonumber(ARGV[1]), tonumber(ARGV[2])
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'at')
            local tokens = tonumber(state[1]) or capacity
            local at = tonumber(state[2]) or now
            tokens = math.min(capacity, tokens + math.max(0, now - at) * capacity / window)
            if tokens < 1 then return math.max(1, math.ceil((1 - tokens) * window / capacity)) end
            redis.call('HSET', KEYS[1], 'tokens', tokens - 1, 'at', now)
            redis.call('PEXPIRE', KEYS[1], window)
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final byte[] edgeSecret;
    private final boolean enabled;

    public ApiRateLimiter(StringRedisTemplate redis,
            @Value("${app.rate-limit.edge-secret:}") String edgeSecret,
            @Value("${app.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.edgeSecret = edgeSecret.getBytes(StandardCharsets.UTF_8);
        this.enabled = enabled;
    }

    public String client(HttpServletRequest request) {
        String proof = request.getHeader("X-Notes-Edge-Key");
        String ip = request.getHeader("CF-Connecting-IP");
        // Forwarded IPs are trusted only with the header overwritten by our Cloudflare rule.
        if (edgeSecret.length >= 32 && proof != null && proof.length() <= 256
                && MessageDigest.isEqual(edgeSecret, proof.getBytes(StandardCharsets.UTF_8))
                && ip != null && ip.length() <= 45 && ip.matches("[0-9a-fA-F:.]+")) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    public void check(String budget, String identity, int capacity, int seconds) {
        if (!enabled) return;
        try {
            Long wait = redis.execute(BUCKET, List.of("auth:rate-limit:"+budget+":"+DigestUtils.sha256Hex(identity)),
                    Integer.toString(capacity), Long.toString(seconds * 1000L));
            if (wait == null) throw new IllegalStateException("Missing rate limit result");
            if (wait > 0) {
                ErrorResponseException error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
                error.getHeaders().set("Retry-After", Long.toString((wait + 999) / 1000));
                throw error;
            }
        } catch (DataAccessException | IllegalStateException error) {
            // Do not enqueue unchecked auth work if the shared limiter is unavailable.
            ErrorResponseException unavailable = new ErrorResponseException(HttpStatus.SERVICE_UNAVAILABLE);
            unavailable.getHeaders().set("Retry-After", "5");
            throw unavailable;
        }
    }

    public void login(HttpServletRequest request, String username) {
        check("login-account", client(request)+"\n"+String.valueOf(username), 10, 300);
    }
}
