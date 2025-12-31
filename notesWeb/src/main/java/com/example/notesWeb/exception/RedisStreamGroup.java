package com.example.notesWeb.exception;

import io.lettuce.core.RedisBusyException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisStreamGroup {
    private final RedisTemplate<String, Object> redisTemplate;

    @EventListener(ApplicationReadyEvent.class)
    public void initGroup() {
        createGroup("auth:login:stream", "auth-group");
        createGroup("notes:create:stream", "notes-group");
        createGroup("media:create:stream", "media-group");
    }

    private void createGroup(String streamKey, String group) {
        try {
            //Ensure situation Stream existed,if not Stream not able to create Consumer group
            Boolean exists = redisTemplate.hasKey(streamKey);
            if (Boolean.FALSE.equals(exists)) {
                redisTemplate.opsForStream().add(
                        StreamRecords.newRecord()
                                .ofObject("init")
                                .withStreamKey(streamKey)
                );
            }

            //Create group
            redisTemplate.opsForStream()
                    .createGroup(streamKey, ReadOffset.latest(), group);

            log.info("Group [{}] created for stream [{}]", group, streamKey);
        }catch (RedisBusyException e) {
            log.info("Redis group [{}] already existed for stream [{}]", group, streamKey);
        }catch (Exception e) {
            log.error("Failed to create group [{}] for stream [{}]", group, streamKey, e);
        }
    }
}
