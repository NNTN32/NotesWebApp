package com.example.notesWeb.dtos.NoteDto;

import com.example.notesWeb.model.takeNotes.MediaType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MediaNoteRequest {
    @Schema(type = "string", format = "binary", description = "Upload file media")
    private MultipartFile file;
}
