package com.example.notesWeb.dtos.NoteDto;

import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteRequest {
    private String content;
    private String title;
}
