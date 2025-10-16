package com.example.notesWeb.service.takeNotes;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.model.takeNotes.MediaType;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.noteRepo.MediaRepo;
import com.example.notesWeb.repository.noteRepo.NotesRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaNoteService {
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;
    private final Cloudinary cloudinary;

    //Logic handle about upload file on notes like photo, video, audio,...
    public NoteMedia uploadMedia(MediaNoteRequest mediaNoteRequest, UUID postID){
        MultipartFile file = mediaNoteRequest.getFile();

        //Check file input
        if (file == null || file.isEmpty()){
            throw new IllegalArgumentException("File can't be null or empty!");
        }

        //File size limit
        if (file.getSize() > 50 * 1024 * 1024){
            throw new IllegalArgumentException("File size exceeds the 50MB limit!");
        }

        //Check for valid file types
        validateFileType(file);

        Notes notes = notesRepo.findById(postID)
                .orElseThrow(() -> new UsernameNotFoundException("Post doesn't exist!"));

        try{
            log.info("Uploading file '{}' ({} bytes) for note {} ", file.getOriginalFilename(), file.getSize(), postID);

            //Upload with retry 3 times
            Map<String, Object> uploadResult = uploadRetry(file, 3);
            String url = uploadResult.get("secure_url").toString();
            String resourceType = uploadResult.get("resource_type").toString();

            NoteMedia noteMedia = new NoteMedia();
            noteMedia.setUrl(url);
            noteMedia.setType(MediaType.valueOf(resourceType.toUpperCase()));
            noteMedia.setNotes(notes);

            mediaRepo.save(noteMedia);
            log.info("Uploaded media successfully for note {} at {}", postID, url);
            return noteMedia;
        }catch (Exception e){
            log.error("Failed to upload file '{}' for note {}: {}",
                    file.getOriginalFilename(), postID, e.getMessage(), e);
            throw new RuntimeException("Failed to upload media: " + e.getMessage());
        }
    }

    //Logic define kind of media upload
    private void validateFileType(MultipartFile file){
        String contentType = file.getContentType();
        if(contentType == null){
            throw new IllegalArgumentException("Can't detect file type!");
        }

        if(!contentType.startsWith("IMAGE/") && !contentType.startsWith("VIDEO") && !contentType.startsWith("AUDIO")){
            throw new IllegalArgumentException("Invalid file type! Only image, video or audio are allowed!");
        }
    }

    //Situation if uploaded file error into Cloudinary
    private Map<String, Object> uploadRetry(MultipartFile file, int maxRetries) throws Exception{
        int attempt = 0;
        IOException lastException = null;

        while (attempt < maxRetries){
            attempt++;
            try(InputStream inputStream = file.getInputStream()){
                Map<String, Object> result = cloudinary.uploader().upload(
                        inputStream,
                        ObjectUtils.asMap(
                                "folder", "notes_uploads/",
                                "resource_type", "auto"
                        )
                );
                return result;
            }catch (IOException e){
                lastException = e;
                log.warn("Upload attempt {}/{} failed for '{}': {}",
                        attempt, maxRetries, file.getOriginalFilename(), e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                }catch (InterruptedException interruptedException){}
            }
        }
        throw new IOException("Upload failed after " + maxRetries + "attempts: " + lastException.getMessage());
    }
}
