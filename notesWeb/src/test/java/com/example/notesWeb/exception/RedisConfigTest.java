package com.example.notesWeb.exception;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;

import static org.assertj.core.api.Assertions.assertThat;

class RedisConfigTest {
    @Test
    void passesAclCredentialsAndDatabaseToTheConnectionFactory() {
        RedisProperties properties = new RedisProperties();
        properties.setHost("private-redis");
        properties.setPort(6380);
        properties.setUsername("notes_app");
        properties.setPassword("test-only-password");
        properties.setDatabase(2);

        var factory = new RedisConfig().connectionFactory(properties);
        var config = factory.getStandaloneConfiguration();
        assertThat(config.getHostName()).isEqualTo("private-redis");
        assertThat(config.getPort()).isEqualTo(6380);
        assertThat(config.getDatabase()).isEqualTo(2);
        assertThat(config.getUsername()).isEqualTo("notes_app");
        assertThat(config.getPassword().get()).containsExactly("test-only-password".toCharArray());
    }

    @Test
    void retainsUnauthenticatedLocalDevelopmentConfiguration() {
        var factory = new RedisConfig().connectionFactory(new RedisProperties());
        assertThat(factory.getStandaloneConfiguration().getPassword().isPresent()).isFalse();
        assertThat(factory.getStandaloneConfiguration().getHostName()).isEqualTo("localhost");
    }
}
