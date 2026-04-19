package com.example.notesWeb.controller.Notes;

import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.exception.kafka.kafkaNoteProducer;
import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.exception.redis.noteRedis.NoteRedisProducer;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import com.example.notesWeb.service.FailedException;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import com.sun.security.auth.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notes")
public class NoteController {
    @Autowired
    private kafkaNoteProducer kafkaNoteProducer;

    @Autowired
    private CreateNoteService createNoteService;

    @Autowired
    private jwtProvider jwtProvider;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TaskNoteService taskNoteService;

    @Autowired
    private NoteRedisProducer noteRedisProducer;

    @Autowired
    private NotesRepo notesRepo;

    //API Handle Create Notes
    @Operation(summary = "User create notes")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/creates")
    public ResponseEntity<?>create(
            @RequestHeader("Authorization") String authorHeader,
            @RequestBody NoteRequest noteRequest){
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

            //Send message queue into Redis Stream
            noteRedisProducer.sendNoteRequest(noteRequest, username);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body("Note creation request queued success for user: " + username);

        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not create notes!");
        }
    }

    //API Handle Get List Notes
    @Operation(summary = "Get all list notes of user")
    @GetMapping("/listNotes")
    public ResponseEntity<?> getAllNotesBasedUser(@AuthenticationPrincipal AuthResponse currentUser){
        //Calling back logic get all list notes from service class
        try{
            List<Notes> notesList = taskNoteService.getAllListNote(currentUser.getId());
            return ResponseEntity.ok(notesList);
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Notes!");
        }
    }

    //API Handle Get List NotesID
    @Operation(summary = "Get id of notes")
    @GetMapping("/{noteID}")
    public ResponseEntity<?> getNoteByID(@PathVariable UUID noteID,
                                         @AuthenticationPrincipal AuthResponse currentUser) {
        try {
            NoteResponse noteResponseList = taskNoteService.getListNoteID(noteID, currentUser.getId());
            return ResponseEntity.ok(noteResponseList);
        } catch (FailedException e) {
            // Return message case can't fine NotesID
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Other unexpected error cases
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }

    //API Handle Delete Notes By User
    @Operation(summary = "The user deleted the proprietary note")
    @DeleteMapping("/delete/{noteID}")
    public ResponseEntity<?> deleteNote(@PathVariable UUID noteID, @AuthenticationPrincipal AuthResponse currentUser){
        try{
            taskNoteService.deleteNote(noteID, currentUser.getId());
            return ResponseEntity.ok("Note Deleted Successfully");
        }catch (FailedException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Notes to delete!");
        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }

    //API Handle Update Notes By User
    @Operation(summary = "Users receive exclusive updates to their notes")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/update/{noteID}")
    public ResponseEntity<?>updateNotes(
            @PathVariable UUID noteID,
            @AuthenticationPrincipal AuthResponse currentUser,
            @RequestBody NoteRequest noteRequest){
        try{
            boolean ownerNote = notesRepo.existsByIdAndUserId(noteID, currentUser.getId());
            if (!ownerNote) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You are not allowed to edit this note!");
            }
            //Notes updated = taskNoteService.updateNote(noteRequest, noteID, userID);
            NoteUpdateEvent event = new NoteUpdateEvent(noteID, currentUser.getId(), currentUser.getUsername(), noteRequest);
            //return ResponseEntity.ok(updated);
            kafkaNoteProducer.sendNoteUpdate(event);
            return ResponseEntity.accepted().body("Updated request submitted to Kafka");

        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage() + "User doesn't have authority to update Notes!");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Notes doesn't exist!");
        }catch (Exception e){
            return ResponseEntity.internalServerError().body(e.getMessage() + "Failed to update Notes!");
        }
    }
}
