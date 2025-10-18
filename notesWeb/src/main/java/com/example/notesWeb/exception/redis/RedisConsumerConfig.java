package com.example.notesWeb.exception.redis;


import com.example.notesWeb.exception.redis.authRedis.AuthRedisConsumer;
import com.example.notesWeb.exception.redis.mediaNoteRedis.MediaRedisConsumer;
import com.example.notesWeb.exception.redis.noteRedis.NoteRedisConsumer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConsumerConfig {
    private final AuthRedisConsumer authRedisConsumer;
    private final NoteRedisConsumer noteRedisConsumer;
    private final MediaRedisConsumer mediaRedisConsumer;

    @PostConstruct
    public void startAllConsumers() {
        log.info("Starting all Redis Stream Consumers....");

        //Each instance have own name consumer (ensure load balancing)
        //Assign instanceID to ENV (not random UUID each time)
        String instanceID = System.getenv().getOrDefault("INSTANCE_ID", UUID.randomUUID().toString().substring(0, 6));

        authRedisConsumer.startConsumer("consumer-1" + instanceID);
        noteRedisConsumer.noteConsumer("consumer-2" + instanceID);
        mediaRedisConsumer.mediaConsumer("consumer-3" + instanceID);

        log.info("All Redis Consumers started for instance: {}", instanceID);
    }
}
