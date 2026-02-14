package com.example.notesWeb.exception.redis;


import com.example.notesWeb.exception.redis.authRedis.AuthRedisConsumer;
import com.example.notesWeb.exception.redis.mediaNoteRedis.MediaRedisConsumer;
import com.example.notesWeb.exception.redis.noteRedis.NoteRedisConsumer;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisConsumerConfig {
    private final AuthRedisConsumer authRedisConsumer;
    private final NoteRedisConsumer noteRedisConsumer;
    private final MediaRedisConsumer mediaRedisConsumer;

    @EventListener(ApplicationReadyEvent.class)
    public void startAllConsumers() {
        log.info("Starting all Redis Stream Consumers....");

        //Each instance have own name consumer (ensure load balancing)
        //Assign instanceID to ENV (not random UUID each time)
        String instanceID = System.getenv()
                .getOrDefault("INSTANCE_ID", UUID.randomUUID().toString().substring(0, 6));

        Executors.newSingleThreadScheduledExecutor()
                        .schedule(() -> {
                            authRedisConsumer.start("auth-" + instanceID);
                            noteRedisConsumer.start("note-" + instanceID);
                            mediaRedisConsumer.start("media-" + instanceID);
                        }, 3, TimeUnit.SECONDS);

        log.info(" Redis Consumers started with instanceID={}", instanceID);
    }
}
