package com.example.notesWeb.dtos.NoteDto;

import com.example.notesWeb.model.takeNotes.MediaType;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@Getter
@RequiredArgsConstructor
public class NoteRequest {
    private String content;
    private LocalDateTime createdAt;
}
