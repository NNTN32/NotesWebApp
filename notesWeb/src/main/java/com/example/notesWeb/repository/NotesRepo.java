package com.example.notesWeb.repository;

import com.example.notesWeb.model.Notes;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotesRepo extends JpaRepository<Notes, Long> {
}
