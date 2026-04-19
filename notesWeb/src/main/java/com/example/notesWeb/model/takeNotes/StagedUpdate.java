package com.example.notesWeb.model.takeNotes;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "staging_notes")
public class StagedUpdate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private UUID noteID;
    private UUID userID;

    @Column(columnDefinition = "TEXT")
    private String rawPayload; //Save whole JSON of event for re-handle

    private String errorReason;
    private int retryCount;
    private LocalDateTime createdAt;
}
