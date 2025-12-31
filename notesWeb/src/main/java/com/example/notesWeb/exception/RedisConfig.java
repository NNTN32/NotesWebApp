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
    //Connect port running RedisStream
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


    //Redis only save key/value type String
    //Provide key-value, redis stream, cache/pubSub/stream consumer
    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory connectionFactory){
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        //Compatible with Redis Stream + JSON when passing object input prevent of error
        // by input binary key, no-group
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }
}
