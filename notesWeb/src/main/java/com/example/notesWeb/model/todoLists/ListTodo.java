package com.example.notesWeb.model.todoLists;

import com.example.notesWeb.model.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "toDo", indexes = {@Index(name = "id_Todo", columnList = "trigger_at, state")})
public class ListTodo {
    @Id
//    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
//    @GeneratedValue
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID idList;

    @Column(length = 255, nullable = false)
    private String heading;

    @Column(columnDefinition = "TEXT")
    private String purport;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    private Instant deadlineTime;

    private Long reminderTime;

    @Column
    private Boolean reminded = false;

    private Instant triggerAt;

    @Enumerated(EnumType.STRING)
    private State state = State.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}
