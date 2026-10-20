package com.example.notesWeb.platform.health;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RedisPingHealthConfigurationTest {
    @Test void healthUsesOnlyPingAndClosesTheConnection() {
        var factory = mock(RedisConnectionFactory.class);
        var connection = mock(RedisConnection.class);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        assertThat(new RedisPingHealthConfiguration().redisHealthIndicator(factory).health().getStatus())
                .isEqualTo(Status.UP);
        verify(connection).ping();
        verify(connection).close();
        verifyNoMoreInteractions(connection);
    }

    @Test void failuresReturnDownWithoutLeakingCredentials() {
        var factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(new IllegalStateException("secret-credential"));
        var health = new RedisPingHealthConfiguration().redisHealthIndicator(factory).health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).isEmpty();
    }
}
