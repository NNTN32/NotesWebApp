package com.example.notesWeb.controller;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    //Api Handle Register
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody AuthRequest request){
        try{
            String result = authService.register(request);
            return ResponseEntity.ok(result);
        }catch (IllegalArgumentException e){
            return ResponseEntity.badRequest().body(e.getMessage() + "Can not register user!");
        }
    }
}
