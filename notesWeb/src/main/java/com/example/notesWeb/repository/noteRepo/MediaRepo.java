package com.example.notesWeb.repository.noteRepo;

import com.example.notesWeb.model.takeNotes.NoteMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaRepo extends JpaRepository<NoteMedia, UUID> {
}
