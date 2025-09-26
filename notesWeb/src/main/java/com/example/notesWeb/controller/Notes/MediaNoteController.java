package com.example.notesWeb.controller.Notes;

import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.service.takeNotes.MediaNoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/media")
public class MediaNoteController {
    @Autowired
    private  MediaNoteService mediaNoteService;

    //API Handle Uploaded Media Notes
    @PostMapping("/uploads/{postID}")
    public ResponseEntity<?> upload(
            @PathVariable String postID,

            //Use ModelAttribute make automatically bind all form data to the DTO object (easy maintain).
            //Only user RequestParam to upload only one file without metadata
            @ModelAttribute MediaNoteRequest mediaNoteRequest
    ){
        NoteMedia noteMedia = mediaNoteService.uploadMedia(mediaNoteRequest, postID);
        return ResponseEntity.ok(noteMedia);
    }
}
