package com.example.notesWeb.dtos;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class AuthResponse {
    private String token;
    private Long id;
    private String userName;
    private Role role;
    private Status status;
    private String message;
}
