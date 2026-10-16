package com.example.notesWeb.notes.media.api;

import com.example.notesWeb.auth.contract.AuthResponse;
import com.example.notesWeb.notes.media.messaging.MediaRedisProducer;
import com.example.notesWeb.notes.media.application.TaskMediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/media")
public class MediaNoteController {
    private final TaskMediaService taskMediaService;
    private final MediaRedisProducer mediaRedisProducer;

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<?> accessDenied() {
        return ResponseEntity.status(403).body("Access denied");
    }

    @PostMapping(value = "/uploads/{postID}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> upload(@PathVariable UUID postID,
            @AuthenticationPrincipal AuthResponse currentUser,
            @RequestPart("file") MultipartFile file) {
        mediaRedisProducer.sendMediaRequest(currentUser.getUsername(), currentUser.getId(), postID, file);
        return ResponseEntity.accepted().body("Upload accepted");
    }

    @DeleteMapping("/delete/{mediaID}")
    public ResponseEntity<?> deleteNotesFile(@PathVariable UUID mediaID, @RequestParam UUID noteId,
            @AuthenticationPrincipal AuthResponse currentUser) {
        taskMediaService.deleteMedia(noteId, mediaID, currentUser.getId());
        return ResponseEntity.ok("File deleted successfully");
    }
}
