package com.example.notesWeb.dtos;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
public class AuthResponse {
    private String token;
    private UUID id;
    private String username;
    private Role role;
    private Status status;
    private String message;
}
