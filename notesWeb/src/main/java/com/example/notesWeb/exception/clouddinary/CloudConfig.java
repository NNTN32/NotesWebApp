package com.example.notesWeb.exception.clouddinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CloudConfig {
    @Bean
    public Cloudinary cloudinary(){
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "du8xjadqd",
                "api_key", "623618121349379",
                "api_secret", "K25p1_7oiCHW-9K-ftfoSKSDiyE"
        ));
    }
}
