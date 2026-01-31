package com.example.notesWeb.repository.todoRepo;

import com.example.notesWeb.model.todoLists.ListTodo;
import com.example.notesWeb.model.todoLists.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepo extends JpaRepository<ListTodo, UUID> {
    List<ListTodo> findByUserId(UUID userId);
    @Query("SELECT t FROM ListTodo t JOIN FETCH t.user WHERE t.idList = :id")
    Optional<ListTodo> findByIdWithUser(@Param("id") UUID id);

    @Query("SELECT t FROM ListTodo t WHERE t.state = :state AND t.triggerAt BETWEEN :now AND :window")
    List<ListTodo> findUpcomingReminder(@Param("state") State state,
                                        @Param("now") Instant now,
                                        @Param("window") Instant window);
}

