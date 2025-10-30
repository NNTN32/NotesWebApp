package com.example.notesWeb.dtos.NoteDto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteUpdateEvent {
    private UUID noteID;
    private UUID userID;
    private NoteRequest noteRequest;
}
