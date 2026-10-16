package com.example.notesWeb.platform.messaging;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import javax.sql.DataSource;

@Configuration
public class MessagingConfig {
    @Bean
    InitializingBean messagingSchema(DataSource dataSource,
            @Value("${app.messaging.initialize-schema:false}") boolean initialize) {
        // Opt-in for disposable CI/dev only. Production applies reviewed SQL before starting.
        return () -> {
            if (initialize) new ResourceDatabasePopulator(new ClassPathResource("db/messaging.sql")).execute(dataSource);
            var jdbc = new org.springframework.jdbc.core.JdbcTemplate(dataSource);
            // Fail before accepting traffic if the reviewed migration was not applied.
            jdbc.queryForList("SELECT sequence_id,id FROM message_outbox WHERE false");
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM message_outbox WHERE kind='KAFKA' AND status='PENDING')",Boolean.class)))
                throw new IllegalStateException("Reconcile pending Kafka outbox commands before starting the Rabbit-only application");
            for (String table : java.util.List.of("message_inbox","message_attempt","message_dead_letter","reminder_notification")) {
                jdbc.queryForList("SELECT * FROM "+table+" WHERE false");
            }
        };
    }
}
