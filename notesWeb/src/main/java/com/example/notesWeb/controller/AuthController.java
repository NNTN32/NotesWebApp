package com.example.notesWeb.controller;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.exception.AuthRedisProducer;
import com.example.notesWeb.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private AuthRedisProducer authRedisProducer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

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

    //Api handle Login sending request Redis
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest authRequest){
        try{
            //Using UUID prevent same id user login
            String sessionId = UUID.randomUUID().toString();
            authRequest.setSessionID(sessionId);
            authRedisProducer.sendLoginRequest(authRequest);

            //Return sessionId on response by using hasmap
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("message", "Login request queued");
            response.put("status", "PENDING");

            return ResponseEntity.ok(response);

        }catch (IllegalArgumentException e){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    //Api handle get sessionID help user login
    @GetMapping("/login/result/{sessionId}")
    public ResponseEntity<?> getLoginResult(@PathVariable String sessionId){
        String key = "login:result:" + sessionId;
        Map<Object, Object> result = redisTemplate.opsForHash().entries(key);
        if(result == null || result.isEmpty()){
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PENDING", "message", "Processing..."));
        }
        return ResponseEntity.ok(result);
    }

}
