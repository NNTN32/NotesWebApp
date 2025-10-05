package com.example.notesWeb.controller.Notes;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.dtos.NoteDto.NoteResponse;
import com.example.notesWeb.model.User;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.UserRepo;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import org.aspectj.weaver.ast.Not;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/notes")
public class NoteController {
    @Autowired
    private CreateNoteService createNoteService;

    @Autowired
    private jwtProvider jwtProvider;

    @Autowired
    private UserRepo userRepo;

    @Autowired
    private TaskNoteService taskNoteService;

    //API Handle Create Notes
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

            //Calling logic handle create Notes from service class
            Notes newNote = createNoteService.createNote(noteRequest, username);
            return ResponseEntity.ok(newNote);

        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not create notes!");
        }
    }

    //API Handle Get List Notes
    @GetMapping("/listNotes/{userID}")
    public ResponseEntity<?> getAllNotesBasedUser(@PathVariable Long userID){
        //Calling back logic get all list notes from service class
        try{
            List<Notes> notesList = taskNoteService.getAllListNote(userID);
            return ResponseEntity.ok(notesList);
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Notes!");
        }
    }

    //API Handle Get List NotesID
    @GetMapping("/{noteID}")
    public ResponseEntity<?> getNoteByID(@PathVariable Long noteID) {
        try {
            List<NoteResponse> noteResponseList = taskNoteService.getListNoteID(noteID);
            return ResponseEntity.ok(noteResponseList);
        } catch (IllegalArgumentException e) {
            // Return message case can't fine NotesID
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            // Other unexpected error cases
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }

    //API Handle Delete Notes By User
    @DeleteMapping("/delete/{noteID}")
    public ResponseEntity<?> deleteNote(@PathVariable Long noteID, @RequestParam Long userID){
        try{
            taskNoteService.deleteNote(noteID, userID);
            return ResponseEntity.ok("Note Deleted Successfully");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Can not find Notes to delete!");
        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Unexpected error: " + e.getMessage());
        }
    }

    //API Handle Update Notes By User
    @PutMapping("/update/{noteID}")
    public ResponseEntity<?>updateNotes(
            @PathVariable Long noteID,
            @RequestHeader("Authorization") String authorHeader,
            @RequestBody NoteRequest noteRequest){
        try{
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
            Long userID = user.getId();

            Notes updated = taskNoteService.updateNote(noteRequest, noteID, userID);
            return ResponseEntity.ok(updated);

        }catch (AccessDeniedException e){
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage() + "User doesn't have authority to update Notes!");
        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage() + "Notes doesn't exist!");
        }catch (Exception e){
            return ResponseEntity.internalServerError().body(e.getMessage() + "Failed to update Notes!");
        }
    }
}
