package com.example.notesWeb.service.todoLists;


import com.example.notesWeb.dtos.TodoListDto.ReminderMessage;
import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.repository.todoRepo.TodoRepo;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService {
    private final ThreadPoolTaskScheduler taskScheduler;
    private final SimpMessagingTemplate messagingTemplate;
    private final TodoRepo todoRepo;

    @Autowired
    private SimpUserRegistry simpUserRegistry;

    //Log check user actually can get notification
    @Scheduled(fixedRate = 10000)
    public void debugActiveUsers() {
        simpUserRegistry.getUsers().forEach(u ->
                log.info("Active STOMP user: {}", u.getName()));

    }

    //Mapping idTodo -> Scheduled future (for cancel/reschedule)
    //Bug in-memory scheduler -> lost task reminder when restarted
    private final Map<UUID, ScheduledFuture<?>> scheduledFutureMap = new ConcurrentHashMap<>();

    public void scheduleReminder (ListTodo todo){
        //Cancel if have remain todo
        cancelReminder(todo.getIdList());

        if(todo.getDeadlineTime() == null || todo.getReminderTime() == null) {
            log.warn("Todo {} has no deadline/reminder set", todo.getIdList());
            return;
        }

        Instant triggerInstant = todo.getDeadlineTime()
                .minus(todo.getReminderTime())
                .atZone(ZoneId.systemDefault())
                .toInstant();

        //trigger timing
        Date triggerDate = Date.from(triggerInstant);


        if (triggerInstant.isBefore(Instant.now())) {
            log.warn("Reminder time already passed, sending immediately");
        }

        //Situation if trigger have already been past -> sent now (or cancel request)
        Runnable task = () -> {
            try{
                //Take back fresh entity
                ListTodo fresh = todoRepo.findByIdWithUser(todo.getIdList()).orElse(null);
                if (fresh == null) {
                    log.warn("Todo not found at runtime: {}", todo.getIdList());
                    return;
                }

                //If it already reminded then next, prevent bug race condition
                if (Boolean.TRUE.equals(fresh.getReminded())){
                    log.info("Already reminded: {}", fresh.getIdList());
                    return;
                }

                //Send websocket realtime to user
                String username = fresh.getUser().getUsername();


                ReminderMessage message = new ReminderMessage(
                        fresh.getHeading(),
                        "Reminder: " + fresh.getHeading() + "is due at " + fresh.getDeadlineTime()
                );

                if (messagingTemplate == null) {
                    log.warn("MessagingTemplat not ready, skip sending reminder for {}", username);
                    return;
                } else if (messagingTemplate != null) {
                    messagingTemplate.convertAndSendToUser(
                            username,
                            "/queue/reminder",
                            new ReminderMessage(
                                    fresh.getHeading(), "Reminder: " + fresh.getHeading() + "is due at " + fresh.getDeadlineTime()
                            ));
                    log.info("Sent reminder for todo {} to user {}", fresh.getHeading(), username);

                    //Mark as reminded
                    fresh.setReminded(true);
                    todoRepo.save(fresh);
                }

            } catch (Exception e) {
                log.error("Error during reminder task for todo " + todo.getIdList(), e);
            }
        };

        // If triggerDate is before now -> schedule immediately (schedule will run immediately when Date <= now)
        ScheduledFuture<?> future = taskScheduler.schedule(task, triggerDate);
        scheduledFutureMap.put(todo.getIdList(), future);

        log.info("Scheduled reminder for '{}' (id {}) at {}", todo.getHeading(), todo.getIdList(), triggerDate);
    }

    public boolean cancelReminder(UUID idTodo) {
        ScheduledFuture<?> future = scheduledFutureMap.remove(idTodo);
        if (future != null){
            boolean cancelled = future.cancel(false);
            log.info("Cancelled scheduled reminder for {}: {}", idTodo, cancelled);
            return cancelled;
        }
        return false;
    }

    //Method setup new time for to do
    public void updateReminderTime (UUID todoID, LocalDateTime newDeadline, Duration newReminder) {
        ListTodo todo = todoRepo.findByIdWithUser(todoID).orElse(null);
        if (todo == null) {
            log.warn("Todo {} not found for update", todoID);
            return;
        }

        //Update new data
        todo.setDeadlineTime(newDeadline);
        todo.setReminderTime(newReminder);
        todo.setReminded(false);
        todoRepo.save(todo);

        //Reschedule
        scheduleReminder(todo);
    }
}
