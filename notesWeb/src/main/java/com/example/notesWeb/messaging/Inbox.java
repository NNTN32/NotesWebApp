package com.example.notesWeb.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class Inbox {
    private final JdbcTemplate jdbc;
    // The marker and business write must commit or roll back together.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean claim(String key) {
        return jdbc.update("INSERT INTO message_inbox(message_key) VALUES (?) ON CONFLICT DO NOTHING", key) == 1;
    }
}
