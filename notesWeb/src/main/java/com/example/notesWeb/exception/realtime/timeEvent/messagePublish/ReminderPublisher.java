package com.example.notesWeb.exception.realtime.timeEvent.messagePublish;

import com.example.notesWeb.dtos.TodoListDto.notificatePopup.ReminderDelay;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderPublisher {
    private final RabbitTemplate rabbitTemplate;

    public void publishDelay(UUID todoId, long delayMs) {

        rabbitTemplate.convertAndSend(
                "reminder.delay.exchange",
                "reminder.delay.key",
                ReminderDelay.builder()
                        .todoID(todoId)
                        .build(),
                message -> {
                    message.getMessageProperties()
                            .setExpiration(String.valueOf(delayMs));
                    return message;
                }
        );

        log.info("Publish reminder {} with delay {}ms", todoId, delayMs);
    }
}
