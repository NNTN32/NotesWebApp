package com.example.notesWeb.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxSchedule {
    private final OutboxRelay relay;
    @Scheduled(fixedDelayString = "${app.messaging.relay-delay-ms:1000}")
    public void tick() {
        try { for (int i=0;i<20 && relay.publishNext();i++) { /* bounded batch */ } }
        catch (RuntimeException failure) { log.error("Outbox relay failed; pending rows retained"); }
    }
}
