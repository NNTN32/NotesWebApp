package com.example.notesWeb.notes.media;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.example.notesWeb.notes.media.infrastructure.SecurityUploadService;
import com.example.notesWeb.shared.error.SystemException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.data.redis.core.RedisTemplate;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MediaUploadBoundaryTest {
    @Mock RedisTemplate<String, Object> redis;
    @Test void returnsOnlyValidatedMetadataAndRemovesTemporaryFile() throws IOException {
        var cloudinary = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        var temporary = new AtomicReference<File>();
        when(uploader.upload(any(), anyMap())).thenAnswer(call -> {
            File file = call.getArgument(0);
            temporary.set(file);
            assertThat(file).exists();
            assertThat(file).hasBinaryContent(new byte[]{1, 2, 3});
            return Map.of("secure_url", "https://res.cloudinary.com/test/image.png", "resource_type", "image", "extra", 42);
        });
        var result = new SecurityUploadService(cloudinary).uploadCloudinary(
                new MockMultipartFile("file", "image.png", "image/png", new byte[]{1, 2, 3}));
        assertThat(result).containsExactlyInAnyOrderEntriesOf(Map.of(
                "secure_url", "https://res.cloudinary.com/test/image.png", "resource_type", "image"));
        assertThat(temporary.get()).doesNotExist();
    }

    @Test void invalidUploadMetadataNeverReachesRedis() throws IOException {
        var cloudinary = mock(Cloudinary.class);
        var uploader = mock(Uploader.class);
        when(cloudinary.uploader()).thenReturn(uploader);
        when(uploader.upload(any(), anyMap())).thenReturn(Map.of("resource_type", "image"));
        var notes = mock(com.example.notesWeb.notes.persistence.NotesRepo.class);
        var userId = java.util.UUID.randomUUID();var noteId = java.util.UUID.randomUUID();
        when(notes.existsByIdAndUserId(noteId, userId)).thenReturn(true);
        var producer = new com.example.notesWeb.notes.media.messaging.MediaRedisProducer(redis, new SecurityUploadService(cloudinary), notes);
        assertThatThrownBy(() -> producer.sendMediaRequest("alice", userId, noteId,
                new MockMultipartFile("file", "image.png", "image/png", new byte[]{1})))
                .isInstanceOf(SystemException.class);
        verifyNoInteractions(redis);
    }

    @Test void rejectsMissingWronglyTypedOrBlankMetadataAndAlwaysRemovesTemporaryFile() throws IOException {
        for (Map<String, Object> response : java.util.Arrays.<Map<String, Object>>asList(null, Map.of(),
                Map.of("secure_url", 123, "resource_type", "image"),
                Map.of("secure_url", "https://example.test/a", "resource_type", 123),
                Map.of("secure_url", " ", "resource_type", "image"))) {
            var cloudinary = mock(Cloudinary.class);
            var uploader = mock(Uploader.class);
            when(cloudinary.uploader()).thenReturn(uploader);
            var temporary = new AtomicReference<File>();
            when(uploader.upload(any(), anyMap())).thenAnswer(call -> {
                temporary.set(call.getArgument(0));return response;
            });
            assertThatThrownBy(() -> new SecurityUploadService(cloudinary).uploadCloudinary(
                    new MockMultipartFile("file", "image.png", "image/png", new byte[]{1})))
                    .isInstanceOf(SystemException.class).hasCauseInstanceOf(IllegalStateException.class);
            assertThat(temporary.get()).doesNotExist();
        }
    }
}
