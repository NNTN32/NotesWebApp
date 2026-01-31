package com.example.notesWeb.exception.realtime.timeEvent.messagePublish;

import com.example.notesWeb.dtos.TodoListDto.notificatePopup.ReminderDelay;
import com.example.notesWeb.exception.realtime.timeEvent.sentMessage.NotifyRepo;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderQueueConsume {
    private final TodoRepo todoRepo;
    private final NotifyRepo notifyRepo;

    @RabbitListener(queues = "reminder.mg.queue")
    @Transactional
    public void consumeReminder (ReminderDelay message){
        ListTodo todo = todoRepo.findByIdWithUser(message.getTodoID())
                .orElse(null);

        //Cancel if have remain todo
        if (todo == null || todo.getReminded()) return;

        if(todo.getState() != State.PENDING) {
            log.info("Skip duplicate reminder {}", todo.getIdList());
            return;
        }

        notifyRepo.notify(todo);
        todo.setState(State.SENT);
        todo.setReminded(true);
        todoRepo.save(todo);

        log.info("Reminder processed {}", todo.getIdList());
    }
}
