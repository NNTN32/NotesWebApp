package com.example.notesWeb.notes.contract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteUpdateEvent {
    private UUID eventId;
    public NoteUpdateEvent(UUID noteID, UUID userID, String username, NoteRequest request) {
        this.eventId = UUID.randomUUID(); this.noteID = noteID; this.userID = userID;
        this.username = username; this.noteRequest = request;
    }
    private UUID noteID;
    private UUID userID;
    private String username;
    private NoteRequest noteRequest;
}
