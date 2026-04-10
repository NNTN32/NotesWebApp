package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.notesWeb.service.SystemException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityUploadService {
    private final Cloudinary cloudinary;

    public Map uploadCloudinary(MultipartFile file){
        File tempFile = null;
        try{
            if(file == null || file.isEmpty()){
                throw new IllegalArgumentException("File upload is empty");
            }

            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "temp";
            tempFile = File.createTempFile("upload_", "_" + fileName);
            file.transferTo(tempFile);

            String folder = determineFolder(file.getContentType());
            Map<String, Object> options = ObjectUtils.asMap(
                    "resource_type", "auto",
                    "folder", folder,
                    "user_filename", true,
                    "unique_filename", true
            );

            return cloudinary.uploader().upload(tempFile, options);
        } catch (Exception e) {
            log.error("Cloudinary upload error: ", e);
            throw new SystemException("Failed to upload to Cloudinary" , e);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                if (tempFile.delete()) {
                    log.info("Temporary file deleted: {}", tempFile.getAbsoluteFile());
                }else {
                    log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
                }
            }
        }
    }

    private String determineFolder(String contentType) {
        if (contentType == null) return "notes_uploads/others";
        String type = contentType.toLowerCase();
        if (type.startsWith("image/")) return "notes_uploads/images";
        if (type.startsWith("video/")) return "notes_uploads/videos";
        if (type.startsWith("audio/")) return "notes_uploads/audios";
        return "notes_uploads/others";
    }
}
