package com.example.notesWeb.exception;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Configuration
public class RedisConfig {
    //Setup port running Redis
    @Bean
    public LettuceConnectionFactory connectionFactory(){
        return new LettuceConnectionFactory("localhost", 6379);
    }

    //Connect port running Redis
    @Bean
    public RedisTemplate<String, Object> redisTemplate(){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory());
        return template;
    }

}
