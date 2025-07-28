package com.example.notesWeb.dtos;

import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@AllArgsConstructor
@Getter
public class AuthRequest {
    private String email;
    private String username;
    private String password;
    private Role role;
    private Status status;
}
