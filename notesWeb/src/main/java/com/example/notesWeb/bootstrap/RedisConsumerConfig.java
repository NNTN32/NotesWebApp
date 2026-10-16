package com.example.notesWeb.bootstrap;


import com.example.notesWeb.auth.messaging.AuthRedisConsumer;
import com.example.notesWeb.notes.media.messaging.MediaRedisConsumer;
import com.example.notesWeb.notes.messaging.NoteRedisConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

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

        authRedisConsumer.start("auth-" + instanceID);
        noteRedisConsumer.start("note-" + instanceID);
        mediaRedisConsumer.start("media-" + instanceID);

        log.info(" Redis Consumers started with instanceID={}", instanceID);
    }
}
