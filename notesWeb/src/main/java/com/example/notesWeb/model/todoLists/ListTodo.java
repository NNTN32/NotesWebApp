package com.example.notesWeb.model.todoLists;

import com.example.notesWeb.model.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "toDo")
public class ListTodo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idList;

    @Column(length = 100000, nullable = false)
    private String heading;

    @Column(length = 100000)
    private String purport;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;
}
