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

//Responsible for sending messages to RabbitMQ with TTL
public class ReminderPublisher {
    private final RabbitTemplate rabbitTemplate;

    //sent id -> consume load db state of todo list
    public void publishDelay(UUID todoId, long delayMs) {

        rabbitTemplate.convertAndSend(
                "reminder.delay.exchange", //bind with queue : reminder.delay.queue
                "reminder.delay.key", //match binding
                
                //payload
                ReminderDelay.builder()
                        .todoID(todoId) //only sent id list prevent stale data
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
