package com.example.notesWeb.repository.todoRepo;

import com.example.notesWeb.model.todoLists.ListTodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TodoRepo extends JpaRepository<ListTodo, Long> {
    List<ListTodo> findTodoListOfUserId(Long userId);
}
