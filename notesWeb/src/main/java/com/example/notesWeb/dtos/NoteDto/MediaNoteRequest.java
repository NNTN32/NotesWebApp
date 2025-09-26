package com.example.notesWeb.dtos.NoteDto;

import com.example.notesWeb.model.takeNotes.MediaType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaNoteRequest {
    private MultipartFile file;
    private MediaType mediaType;
}
