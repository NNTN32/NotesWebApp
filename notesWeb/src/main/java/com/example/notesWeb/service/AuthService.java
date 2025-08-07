package com.example.notesWeb.service;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.model.Status;
import com.example.notesWeb.model.User;
import com.example.notesWeb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jwtProvider tokenProvider;

    //Logic handle request register user
    public String register(AuthRequest authRequest){
        if(userRepo.existsByUsername(authRequest.getUsername())){
            throw new IllegalArgumentException("Username has already existed!");
        }
        User user = new User();
        user.setUsername(authRequest.getUsername());
        user.setEmail(authRequest.getEmail() + "@example.com");
        user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
        user.setRole(authRequest.getRole());

        userRepo.save(user);
        return "User registered successfully";
    }

    //Logic handle request login user
    public AuthResponse login(AuthRequest request){
        User user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username!"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            throw new IllegalArgumentException("Invalid username");
        }

        String token = tokenProvider.generateToken(user);

        return new AuthResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole(),
                Status.SUCCESS,
                "Login successful"
        );
    }
}
