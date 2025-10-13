package com.example.notesWeb.model.takeNotes;

import com.example.notesWeb.model.User;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "notes")
public class Notes {

    @Id
    @Column(columnDefinition = "uuid DEFAULT get_uuid_v7()")
    @GeneratedValue
    private Long id;

    @Column(length = 100000, nullable = false)
    private String title;

    @Column(length = 100000)
    private String content;


    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference //No loop
    private User user;

    //Add reverse relationship to join table NoteMedia
    @OneToMany(mappedBy = "notes", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true) //Add noteID key constraint on noteMedia side delete by
    @JsonManagedReference
    private List<NoteMedia> noteMediaList;
}
