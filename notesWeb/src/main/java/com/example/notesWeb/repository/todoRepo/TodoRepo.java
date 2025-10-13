package com.example.notesWeb.repository.todoRepo;

import com.example.notesWeb.model.todoLists.ListTodo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TodoRepo extends JpaRepository<ListTodo, UUID> {
    List<ListTodo> findByUserId(UUID userId);}
