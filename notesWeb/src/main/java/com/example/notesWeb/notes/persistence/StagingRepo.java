package com.example.notesWeb.notes.persistence;

import com.example.notesWeb.notes.domain.StagedUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StagingRepo extends JpaRepository<StagedUpdate, Long> {
}
