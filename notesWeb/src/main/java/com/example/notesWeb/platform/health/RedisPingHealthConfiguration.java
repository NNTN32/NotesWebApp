package com.example.notesWeb.platform.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@Configuration(proxyBeanMethods = false)
public class RedisPingHealthConfiguration {
    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory factory) {
        // The application ACL permits PING; Actuator's default INFO check needs broader privileges.
        return () -> {
            try (var connection = factory.getConnection()) {
                return "PONG".equals(connection.ping()) ? Health.up().build() : Health.down().build();
            } catch (Exception ignored) {
                return Health.down().build();
            }
        };
    }
}
