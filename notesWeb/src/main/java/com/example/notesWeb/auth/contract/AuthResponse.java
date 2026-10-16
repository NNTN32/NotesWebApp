package com.example.notesWeb.auth.contract;

import com.example.notesWeb.auth.domain.Role;
import com.example.notesWeb.auth.domain.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@AllArgsConstructor
@Getter
@Builder
public class AuthResponse implements java.security.Principal {
    @Override
    public String getName() { return username; }
    private String token;
    private String rotationSecret;
    private UUID id;
    private String username;
    private Role role;
    private Status status;
    private String message;
}
