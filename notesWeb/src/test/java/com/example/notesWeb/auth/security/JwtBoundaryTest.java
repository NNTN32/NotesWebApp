package com.example.notesWeb.auth.security;


import com.example.notesWeb.auth.domain.User;
import com.example.notesWeb.auth.domain.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class JwtBoundaryTest {
    final jwtProvider tokens = new jwtProvider();
    final User user = new User();
    @BeforeEach void setup() {
        ReflectionTestUtils.setField(tokens, "jwtSecret", "c2VjdXJpdHktcmVncmVzc2lvbi10ZXN0LWtleS0zMi1ieXRlcy1vbmx5");
        ReflectionTestUtils.setField(tokens, "jwtExpiration", 60000L);
        ReflectionTestUtils.setField(tokens, "jwtRefresh", 60000L);
        user.setId(UUID.randomUUID()); user.setUsername("alice"); user.setRole(Role.USER);
    }
    @Test void accessAndRefreshAreNotInterchangeable() {
        String access = tokens.generateToken(user), refresh = tokens.generateRSToken(user);
        assertThat(tokens.validateToken(access)).isTrue();
        assertThat(tokens.validateRefreshToken(access)).isFalse();
        assertThat(tokens.validateRefreshToken(refresh)).isTrue();
        assertThat(tokens.validateToken(refresh)).isFalse();
    }
    @Test void expiredTokensCannotSupplyTrustedClaims() {
        ReflectionTestUtils.setField(tokens, "jwtExpiration", -60000L);
        ReflectionTestUtils.setField(tokens, "jwtRefresh", -60000L);
        String access = tokens.generateToken(user), refresh = tokens.generateRSToken(user);
        assertThat(tokens.validateToken(access)).isFalse();
        assertThat(tokens.validateRefreshToken(refresh)).isFalse();
        assertThatThrownBy(() -> tokens.getUserFromJwt(refresh)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void signedTokenWithoutExpiryFailsClosedWithoutNullPointer() {
        String secret = (String) ReflectionTestUtils.getField(tokens, "jwtSecret");
        String token = io.jsonwebtoken.Jwts.builder().subject(user.getUsername())
                .claim("userId", user.getId().toString()).claim("role", "USER").claim("type", "Access")
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(io.jsonwebtoken.io.Decoders.BASE64.decode(secret)))
                .compact();
        assertThat(tokens.validateToken(token)).isFalse();
        assertThat(tokens.isTokenExpired(token)).isTrue();
        assertThat(tokens.getRemainingTime(token)).isZero();
    }

    @Test void malformedTokensFailClosed() {
        assertThat(tokens.validateToken("not-a-jwt")).isFalse();
        assertThat(tokens.validateRefreshToken("not-a-jwt")).isFalse();
    }
}
