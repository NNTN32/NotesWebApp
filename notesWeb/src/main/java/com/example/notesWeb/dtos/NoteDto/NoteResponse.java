package com.example.notesWeb.dtos.NoteDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteResponse {
    private String content;
    private String title;
    private String url;
}
