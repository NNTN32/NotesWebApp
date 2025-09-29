package com.example.notesWeb.repository;

import com.example.notesWeb.model.takeNotes.Notes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface NotesRepo extends JpaRepository<Notes, Long> {
    List<Notes> findByUserId(Long userID);
    @Query("SELECT * FROM notes JOIN note_media ON notes.id = note_media.note_id")
    List<Notes> findNoteId(Long noteID);
}
