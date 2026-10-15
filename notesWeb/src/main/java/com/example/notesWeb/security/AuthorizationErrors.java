package com.example.notesWeb.security;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuthorizationErrors {
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied() {
        return ResponseEntity.status(403).body("Access denied");
    }
}
