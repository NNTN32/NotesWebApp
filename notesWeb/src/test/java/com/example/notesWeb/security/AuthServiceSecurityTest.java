package com.example.notesWeb.security;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.RegisterRequest;
import com.example.notesWeb.model.*;
import com.example.notesWeb.repository.*;
import com.example.notesWeb.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceSecurityTest {
    @Test void registrationCannotAssignAnAdministrativeRole() throws Exception {
        UserRepo users = mock(UserRepo.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AuthService service = new AuthService(mock(RedisTemplate.class), new ObjectMapper());
        ReflectionTestUtils.setField(service, "userRepo", users);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "idGenerateRepo", mock(IdGenerateRepo.class));
        RegisterRequest input = new ObjectMapper().configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue("{\"username\":\"mallory\",\"password\":\"secret\",\"role\":\"ADMIN\"}", RegisterRequest.class);
        when(encoder.encode("secret")).thenReturn("hashed");
        service.register(input);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.USER);
        assertThat(saved.getValue().getPassword()).isEqualTo("hashed");
    }
    @Test void rejectedRefreshNeverReadsSessionOrGraceKeys() {
        RedisTemplate redis = mock(RedisTemplate.class);
        jwtProvider tokens = mock(jwtProvider.class);
        AuthService service = new AuthService(redis, new ObjectMapper());
        ReflectionTestUtils.setField(service, "tokenProvider", tokens);
        assertThatThrownBy(() -> service.refresh("expired-or-access-token")).isInstanceOf(FailedException.class);
        verifyNoInteractions(redis);
    }
}
