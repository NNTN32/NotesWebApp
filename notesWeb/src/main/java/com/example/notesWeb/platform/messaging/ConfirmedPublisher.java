package com.example.notesWeb.platform.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class ConfirmedPublisher {
    private final RabbitTemplate rabbit;
    private final SimpMessagingTemplate websocket;
    private final ObjectMapper json;
    public void send(UUID id, String kind, String destination, String routingKey, String payload) throws Exception {
        switch (kind) {
            case "RABBIT", "NOTE_UPDATE" -> {
                MessageProperties properties = new MessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                properties.setMessageId(id.toString());
                // A unique correlation id per attempt avoids late confirms settling a later attempt.
                CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
                rabbit.send(destination, routingKey, new Message(payload.getBytes(StandardCharsets.UTF_8), properties), correlation);
                var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) throw new IllegalStateException("Publish not routed/confirmed");
            }
            case "STOMP" -> websocket.convertAndSendToUser(destination, routingKey, json.readTree(payload));
            default -> throw new IllegalArgumentException("Unknown outbox transport");
        }
    }
}
