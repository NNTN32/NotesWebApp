package com.example.notesWeb.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import lombok.Value;
import org.junit.jupiter.api.Order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    //Setup port running Redis
    @Bean
    @Primary
    public LettuceConnectionFactory connectionFactory(
            @Value("${spring.redis.host}") String host,
            @Value("${spring.redis.port}") int port
    ) {
//        return new LettuceConnectionFactory("localhost", 6379);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(host, port);
        factory.setValidateConnection(true);
        return factory;
    }

    //Connect port running RedisStream
    //Redis only save key/value type String
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        return template;
    }

}
