package com.example.notesWeb.exception.realtime.timeEvent.receiveMessage;


import com.example.notesWeb.exception.realtime.timeEvent.messagePublish.ReminderPublisher;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReminderScheduler {
    private final TodoRepo todoRepo;
    private final ReminderPublisher reminderPublisher;


    @Scheduled(fixedDelay = 60000) //each 60s
    @Transactional
    public void pollUpcoming() {

        //use Instant prevent on avoid time zone discrepancies.
        Instant now = Instant.now();
        Instant window = now.plusSeconds(120);

        //Only get task that pending & almost expired
        List<ListTodo> todoList = todoRepo.findUpcomingReminder(State.PENDING, now, window);

        for (ListTodo todo : todoList) {
            long delayMs = Duration.between(now, todo.getTriggerAt()).toMillis();

            if (delayMs < 0) delayMs = 0;
            //Change state so that subsequent scans or other instances will ignore it
            todo.setState(State.QUEUE);
            todoRepo.save(todo);

            reminderPublisher.publishDelay(todo.getIdList(), Math.max(0, delayMs));
            log.info("Scheduled reminder {}", todo.getIdList());
        }
    }
}
