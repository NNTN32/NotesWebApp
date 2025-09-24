package com.example.notesWeb.dtos.NoteDto;

import com.example.notesWeb.model.takeNotes.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaNoteRequest {
    private String url;
    private MediaType mediaType;
}
