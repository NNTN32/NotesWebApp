package com.example.notesWeb.service.takeNotes;

import com.cloudinary.Cloudinary;
import com.example.notesWeb.repository.NotesRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MediaNoteService {
    private final NotesRepo notesRepo;
    private final Cloudinary cloudinary;

    //Logic handle about upload file on notes like photo, video, audio,...

}
