package com.example.notesWeb.controller.Notes;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.takeNotes.MediaNoteService;
import com.example.notesWeb.service.takeNotes.TaskMediaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/media")
public class MediaNoteController {
    @Autowired
    private  MediaNoteService mediaNoteService;

    @Autowired
    private jwtProvider jwtProvider;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TaskMediaService taskMediaService;

    //API Handle Uploaded Media Notes
    @PostMapping("/uploads/{postID}")
    public ResponseEntity<?> upload(
            @PathVariable String postID,
            @RequestHeader("Authorization") String authorHeader,
            //Use ModelAttribute make automatically bind all form data to the DTO object (easy maintain).
            //Only user RequestParam to upload only one file without metadata
            @ModelAttribute MediaNoteRequest mediaNoteRequest
    ){
        try{

            //Check token make sure can get info of user
            //Checking situation missing Bearer or no have Header
            if(authorHeader == null || !authorHeader.startsWith("Bearer ")){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization header is missing or invalid.");
            }

            String token = authorHeader.substring(7);

            //Checking situation if token has expired
            if(jwtProvider.isTokenExpired(token)){
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token expired. Please login again.");
            }

            //Checking situation if user really & still login
            String username = jwtProvider.getUserFromJwt(token);
            User user = userRepo.findByUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found!"));

            //Calling logic handle upload Media Notes from service class
            NoteMedia noteMedia = mediaNoteService.uploadMedia(mediaNoteRequest, postID);
            return ResponseEntity.ok(noteMedia);

        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not create notes!");
        }
    }

    //API Handle Delete MediaNote
    @DeleteMapping("/delete/{mediaID}")
    public ResponseEntity<?> deleteNotesFile(@PathVariable Long mediaID, @RequestParam Long noteId){
        try{
            taskMediaService.deleteMedia(noteId, mediaID);
            return ResponseEntity.ok("File have been deleted success !");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find File to delete!");
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }
}
