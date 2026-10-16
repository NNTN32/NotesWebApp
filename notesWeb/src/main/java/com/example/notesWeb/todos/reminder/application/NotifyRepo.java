package com.example.notesWeb.todos.reminder.application;

import com.example.notesWeb.todos.domain.ListTodo;

public interface NotifyRepo {
    void notify(ListTodo todo);
}
