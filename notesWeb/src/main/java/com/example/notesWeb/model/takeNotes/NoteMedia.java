package com.example.notesWeb.model.takeNotes;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "noteMedia")
public class NoteMedia {
    @Id
    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
    @GeneratedValue
    private Long id;

    private String url;

    @Enumerated(EnumType.STRING)
    private MediaType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "noteId", nullable = false)
    private Notes notes;
}
