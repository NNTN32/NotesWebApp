package com.example.notesWeb.notes.contract;

import com.example.notesWeb.notes.domain.Notes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteCache {
    private UUID id;
    private String title;
    private String content;
    private LocalDateTime updatedAt;

    public static NoteCache fromEntity(Notes notes) {
        return new NoteCache(
                notes.getId(),
                notes.getTitle(),
                notes.getContent(),
                notes.getUpdatedAt()
        );
    }
}
