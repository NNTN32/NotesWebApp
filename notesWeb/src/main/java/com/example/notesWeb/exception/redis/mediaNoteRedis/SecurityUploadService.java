package com.example.notesWeb.exception.redis.mediaNoteRedis;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityUploadService {
    private final Cloudinary cloudinary;

    public String uploadTemporary(MultipartFile file){
        try{
            if(file == null || file.isEmpty()){
                throw new IllegalArgumentException("File upload is empty");
            }

            Map<String, Object> options = ObjectUtils.asMap(
                    "resource_type", "auto",
                    "folder", "temp_uploads/",
                    "user_filename", true,
                    "unique_filename", true
            );

            Map uploadResult = cloudinary.uploader().upload(file.getBytes(), options);
            String url = (String) uploadResult.get("secure_url");
            log.info("Uploaded temporary file to Cloudinary: {}", url);
            return url;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload temporary file: " + e.getMessage(), e);
        }
    }
}
