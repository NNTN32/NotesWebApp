package com.example.notesWeb.notes.media.persistence;

import com.example.notesWeb.notes.media.domain.NoteMedia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MediaRepo extends JpaRepository<NoteMedia, UUID> {
}
