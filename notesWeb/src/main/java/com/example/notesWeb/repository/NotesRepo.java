package com.example.notesWeb.repository;

import com.example.notesWeb.model.takeNotes.Notes;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotesRepo extends JpaRepository<Notes, Long> {
    Optional<Notes> findPostExist(String postID);
}
