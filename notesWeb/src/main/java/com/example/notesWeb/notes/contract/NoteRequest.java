package com.example.notesWeb.notes.contract;

import lombok.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class NoteRequest {
    private String content;
    private String title;
}
