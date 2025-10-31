package com.example.notesWeb.model.takeNotes;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "noteMedia")
public class NoteMedia {
    @Id
    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noteId", nullable = false)
    @JsonBackReference
    private Notes notes;
}
