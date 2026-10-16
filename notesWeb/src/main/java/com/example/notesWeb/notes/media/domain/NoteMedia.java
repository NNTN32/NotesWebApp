package com.example.notesWeb.notes.media.domain;

import com.example.notesWeb.notes.domain.Notes;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "noteMedia")
public class NoteMedia {
    @Id
//    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
//    @GeneratedValue
    @Column(columnDefinition = "uuid", nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    private MediaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noteId", nullable = false)
    private Notes notes;
}
