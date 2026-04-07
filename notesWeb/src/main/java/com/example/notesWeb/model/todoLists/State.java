package com.example.notesWeb.model.todoLists;

public enum State {
    QUEUE, //Push into RabbitMQ (recent time)
    PENDING, //Wait for Scheduler to scan (long time)
    SENT,
    CORRECT,
    FAIL
}
