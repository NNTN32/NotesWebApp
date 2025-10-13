package com.example.notesWeb.repository.noteRepo;

import com.example.notesWeb.model.takeNotes.Notes;
import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotesRepo extends JpaRepository<Notes, UUID> {
    List<Notes> findByUserId(UUID userID);

    //Fix queries using JPQL works with entity and field names in classes & Mapping 2 sides instead of SQL basic
    @Query("SELECT DISTINCT n FROM Notes n LEFT JOIN FETCH n.noteMediaList WHERE n.id = :noteID")
    Optional<Notes> findNoteId(@Param("noteID") UUID noteID);

    @Query("SELECT n FROM Notes n WHERE n.id = :noteID")
    Optional<Notes> findNote(@Param("noteID") UUID noteID);
}
