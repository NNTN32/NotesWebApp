package com.example.notesWeb.controller.Notes;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.exception.redis.mediaNoteRedis.MediaRedisProducer;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.takeNotes.MediaNoteService;
import com.example.notesWeb.service.takeNotes.TaskMediaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

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

    @Autowired
    private MediaRedisProducer mediaRedisProducer;

    //API Handle Uploaded Media Notes
    @Operation(summary = "User uploaded file media on Notes")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(value = "/uploads/{postID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(
            @PathVariable UUID postID,
            @RequestHeader("Authorization") String authorHeader,
            @RequestPart("file")MultipartFile file
    ){
        MediaNoteRequest mediaNoteRequest = new MediaNoteRequest(file);
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

            mediaRedisProducer.sendMediaRequest(username, postID, file);
            return ResponseEntity.accepted().body("Upload accepted");

        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not create notes!");
        }
    }

    //API Handle Delete MediaNote
    @Operation(summary = "User deleted file media on Notes")
    @DeleteMapping("/delete/{mediaID}")
    public ResponseEntity<?> deleteNotesFile(@PathVariable UUID mediaID, @RequestParam UUID noteId){
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
