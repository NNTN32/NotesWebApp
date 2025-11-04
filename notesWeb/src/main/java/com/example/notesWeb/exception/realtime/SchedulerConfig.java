package com.example.notesWeb.exception.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    @Bean
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("ReminderScheduler-");
        scheduler.setRemoveOnCancelPolicy(true); // help avoid memory leak when cancel reminder
        scheduler.initialize();
        return scheduler;
    }
}
