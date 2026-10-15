package com.example.notesWeb.security;

import com.example.notesWeb.dtos.AuthResponse;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.ErrorResponseException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ApiRateLimitTest {
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }

    MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr("192.0.2.1");
        return request;
    }

    @Test void forwardedIpRequiresEdgeProofAndCannotBeOverriddenByXForwardedFor() {
        String secret = "a".repeat(64);
        var limiter = new ApiRateLimiter(mock(StringRedisTemplate.class), secret, true);
        var r = request("POST", "/api/auth/login");
        r.addHeader("CF-Connecting-IP", "198.51.100.2");
        r.addHeader("X-Forwarded-For", "203.0.113.3");
        r.addHeader("X-Notes-Edge-Key", "forged");
        assertThat(limiter.client(r)).isEqualTo("192.0.2.1");
        r.removeHeader("X-Notes-Edge-Key"); r.addHeader("X-Notes-Edge-Key", secret);
        assertThat(limiter.client(r)).isEqualTo("198.51.100.2");
        r.removeHeader("CF-Connecting-IP"); r.addHeader("CF-Connecting-IP", "attacker.example");
        assertThat(limiter.client(r)).isEqualTo("192.0.2.1");
        assertThat(new ApiRateLimiter(mock(StringRedisTemplate.class), "", true).client(r)).isEqualTo("192.0.2.1");
    }

    @Test void loginAndRegisterHaveSeparateBudgetsFromPollingAndPreflight() throws Exception {
        var limiter = mock(ApiRateLimiter.class);
        when(limiter.client(any())).thenReturn("client");
        var filter = new ApiRateLimitFilter(limiter);
        var chain = mock(FilterChain.class);
        for (String path : List.of("/api/auth/login", "/api/auth/register", "/api/auth/login/result/session"))
            filter.doFilter(request(path.contains("result") ? "GET" : "POST", path), new MockHttpServletResponse(), chain);
        filter.doFilter(request("OPTIONS", "/api/auth/login"), new MockHttpServletResponse(), chain);
        filter.doFilter(request("GET", "/actuator/health"), new MockHttpServletResponse(), chain);
        verify(limiter).check("login-ip", "client", 60, 60);
        verify(limiter).check("register-ip", "client", 20, 3600);
        verify(limiter).check("api", "ip:client", 300, 60);
        verify(limiter, times(3)).check(anyString(), anyString(), anyInt(), anyInt());
    }

    @Test void authenticatedUsersBehindOneIpDoNotShareApiBudget() throws Exception {
        var limiter = mock(ApiRateLimiter.class);
        UUID id = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                AuthResponse.builder().id(id).username("alice").build(), null, List.of()));
        new ApiRateLimitFilter(limiter).doFilter(request("GET", "/notes/listNotes"), new MockHttpServletResponse(), mock(FilterChain.class));
        verify(limiter).check("api", "user:"+id, 300, 60);
    }

    @Test void throttledRequestNeverReachesControllerAndReturnsRetryAfter() throws Exception {
        var limiter = mock(ApiRateLimiter.class);
        when(limiter.client(any())).thenReturn("client");
        var error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        error.getHeaders().set("Retry-After", "30");
        doThrow(error).when(limiter).check(anyString(), anyString(), anyInt(), anyInt());
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        new ApiRateLimitFilter(limiter).doFilter(request("POST", "/api/auth/login"), response, chain);
        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("30");
        assertThat(response.getContentAsString()).contains("rate_limited");
        verifyNoInteractions(chain);
    }

    @Test void redisOutageFailsClosedAndKillSwitchDoesNotAccessRedis() {
        var redis = mock(StringRedisTemplate.class);
        when(redis.execute(eq(ApiRateLimiter.BUCKET), anyList(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> new ApiRateLimiter(redis, "", true).check("login", "ip", 10, 60))
                .isInstanceOfSatisfying(ErrorResponseException.class, e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        reset(redis);
        new ApiRateLimiter(redis, "", false).check("login", "ip", 10, 60);
        verifyNoInteractions(redis);
    }

    @Test void accountThrottleIsScopedToAccountAndClientWithoutStoringRawIdentity() {
        var limiter = spy(new ApiRateLimiter(mock(StringRedisTemplate.class), "", true));
        doNothing().when(limiter).check(anyString(), anyString(), anyInt(), anyInt());
        var r = request("POST", "/api/auth/login");
        limiter.login(r, "alice");
        verify(limiter).check("login-account", "192.0.2.1\nalice", 10, 300);
    }

    @Test void accountLimitReturns429BeforeEnqueueAndDoesNotGetCaughtAsBadCredentials() throws Exception {
        var limiter = mock(ApiRateLimiter.class);
        var error = new ErrorResponseException(HttpStatus.TOO_MANY_REQUESTS);
        error.getHeaders().set("Retry-After", "30");
        doThrow(error).when(limiter).login(any(), eq("alice"));
        var controller = new com.example.notesWeb.controller.AuthController();
        var queue = mock(com.example.notesWeb.exception.redis.authRedis.AuthRedisProducer.class);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "rateLimiter", limiter);
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "authRedisProducer", queue);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                .contentType("application/json").content("{\"username\":\"alice\",\"password\":\"unused\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Retry-After", "30"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.error").value("rate_limited"))
                .andExpect(result -> assertThat(result.getResponse().getErrorMessage()).isNull());
        verifyNoInteractions(queue);
    }
}
