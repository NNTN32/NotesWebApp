package com.example.notesWeb.todos.persistence;

import com.example.notesWeb.todos.domain.ListTodo;
import com.example.notesWeb.todos.domain.State;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TodoRepo extends JpaRepository<ListTodo, UUID> {
    List<ListTodo> findByUserId(UUID userId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM ListTodo t JOIN FETCH t.user WHERE t.idList = :id")
    Optional<ListTodo> findLockedById(@Param("id") UUID id);
    @Query("SELECT t FROM ListTodo t JOIN FETCH t.user WHERE t.idList = :id")
    Optional<ListTodo> findByIdWithUser(@Param("id") UUID id);

    //Scan based on Index from class entities
    @Query("SELECT t FROM ListTodo t WHERE (t.state = :state OR t.state = com.example.notesWeb.todos.domain.State.QUEUE) " +
            "AND t.triggerAt <= :window")
    List<ListTodo> findUpcomingReminder(@Param("state") State state,
                                        @Param("window") Instant window);
}

