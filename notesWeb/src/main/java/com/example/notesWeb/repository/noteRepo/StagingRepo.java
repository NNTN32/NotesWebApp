package com.example.notesWeb.repository.noteRepo;

import com.example.notesWeb.model.takeNotes.StagedUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingRepo extends JpaRepository<StagedUpdate, Long> {
}
