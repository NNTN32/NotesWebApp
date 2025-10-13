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
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MediaNoteService {
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;
    private final Cloudinary cloudinary;

    //Logic handle about upload file on notes like photo, video, audio,...
    public NoteMedia uploadMedia(MediaNoteRequest mediaNoteRequest, UUID postID){
        Notes notes = notesRepo.findById(postID)
                .orElseThrow(() -> new UsernameNotFoundException("Post doesn't exist!"));
        try{
            //Logic handle upload file into Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    //Parameters file users sent from FE to Sever
                    mediaNoteRequest.getFile().getBytes(),

                    //Parameters configuration options for uploading
                    ObjectUtils.emptyMap()
            );

            //Get link URL for Sever
            String url = uploadResult.get("secure_url").toString();
            String resourceType = uploadResult.get("resource_type").toString(); // image / video / raw

            //Create entity & save link into DB
            NoteMedia noteMedia = new NoteMedia();
            noteMedia.setUrl(url);
            noteMedia.setType(MediaType.valueOf(resourceType.toUpperCase()));
            noteMedia.setNotes(notes);

            return mediaRepo.save(noteMedia);
        }catch (IOException e){
            throw new RuntimeException("Failed to upload media: " + e.getMessage());
        }
    }

    //Logic define kind of media upload
    private String getResourceType(MediaType mediaType){
        switch (mediaType){
            case IMAGE:
                return "image";
            case VIDEO:
                return "video";
            case RAW:
                return "auto";//Let Cloudinary defined
            default:
                return "auto";//Save fallback
        }
    }
}
