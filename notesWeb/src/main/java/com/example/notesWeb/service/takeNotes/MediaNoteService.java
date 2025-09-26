package com.example.notesWeb.service.takeNotes;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.notesWeb.dtos.NoteDto.MediaNoteRequest;
import com.example.notesWeb.model.takeNotes.MediaType;
import com.example.notesWeb.model.takeNotes.NoteMedia;
import com.example.notesWeb.model.takeNotes.Notes;
import com.example.notesWeb.repository.MediaRepo;
import com.example.notesWeb.repository.NotesRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MediaNoteService {
    private final NotesRepo notesRepo;
    private final MediaRepo mediaRepo;
    private final Cloudinary cloudinary;

    //Logic handle about upload file on notes like photo, video, audio,...
    public NoteMedia uploadMedia(MediaNoteRequest mediaNoteRequest, String postID){
        Notes notes = notesRepo.findById(Long.parseLong(postID))
                .orElseThrow(() -> new UsernameNotFoundException("Post doesn't exist!"));
        try{
            //Logic handle upload file into Cloudinary
            Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    //Parameters file users sent from FE to Sever
                    mediaNoteRequest.getFile().getBytes(),

                    //Parameters configuration options for uploading
                    ObjectUtils.asMap(
                            "resource_type", getResourceType(mediaNoteRequest.getMediaType())
                    )
            );

            //Get link URL for Sever
            String url = uploadResult.get("secure_url").toString();

            //Create entity & save link into DB
            NoteMedia noteMedia = new NoteMedia();
            noteMedia.setUrl(url);
            noteMedia.setType(mediaNoteRequest.getMediaType());
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
            case AUDIO:
                return "auto";//Let Cloudinary defined
            default:
                return "auto";//Save fallback
        }
    }
}
