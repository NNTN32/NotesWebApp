package com.example.notesWeb.exception;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;

@Component
@RequiredArgsConstructor
public class RedisStreamGroup {
    private final RedisTemplate<String,Object> redisTemplate;
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void initGroup() {
        createGroup("auth:login:stream","auth-group");
        createGroup("notes:create:stream","notes-group");
        createGroup("media:create:stream","media-group");
    }
    private void createGroup(String stream,String group) {
        try {
            // Spring Data sends MKSTREAM. No dummy poison entry; queued-before-start work is retained.
            redisTemplate.opsForStream().createGroup(stream,ReadOffset.from("0-0"),group);
        } catch (DataAccessException failure) {
            String reason=failure.getMostSpecificCause().getMessage();
            if (reason==null || !reason.startsWith("BUSYGROUP")) throw failure;
        }
    }
}
