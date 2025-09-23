package com.example.notesWeb.dtos.NoteDto;

import com.example.notesWeb.model.takeNotes.MediaType;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import lombok.*;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteRequest {
    private String content;
    private String title;
    private LocalDateTime createdAt;
}
