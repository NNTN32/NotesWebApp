package com.example.notesWeb.exception.realtime.timeEvent.sentMessage;

import com.example.notesWeb.model.todoLists.ListTodo;

public interface NotifyRepo {
    void notify(ListTodo todo);
}
