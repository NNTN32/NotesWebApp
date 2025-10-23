package com.example.notesWeb.model.todoLists;

import com.example.notesWeb.model.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "toDo")
public class ListTodo {
    @Id
    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
    @GeneratedValue
    private UUID idList;

    @Column(length = 100000, nullable = false)
    private String heading;

    @Column(length = 100000)
    private String purport;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    private LocalDateTime deadlineTime;

    private Duration reminderTime;

    @Column
    private Boolean reminded = false;

    @Enumerated(EnumType.STRING)
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}
