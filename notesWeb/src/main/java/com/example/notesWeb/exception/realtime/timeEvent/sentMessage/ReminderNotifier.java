package com.example.notesWeb.exception.realtime.timeEvent.sentMessage;

import com.example.notesWeb.dtos.TodoListDto.notificatePopup.ReminderMessage;
import com.example.notesWeb.model.todoLists.ListTodo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReminderNotifier implements NotifyRepo{
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void notify(ListTodo listTodo) {
        //send websocket
        String userName = listTodo.getUser().getUsername();

        messagingTemplate.convertAndSendToUser(
                userName,
                "/queue/reminder",
                ReminderMessage.builder()
                        .todoID(listTodo.getIdList())
                        .title("Hello there " + userName)
                        .message("It's time to: " + listTodo.getHeading())
                        .build()
        );

        log.info("Sent notify user {}", userName);
    }
}
