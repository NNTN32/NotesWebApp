package com.example.notesWeb.dtos;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Builder
public class AuthResponse {
    private String token;
    private String rotationSecret;
    private UUID id;
    private String username;
    private Role role;
    private Status status;
    private String message;
}
