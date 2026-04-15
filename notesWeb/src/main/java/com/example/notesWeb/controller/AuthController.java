package com.example.notesWeb.controller;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.exception.redis.authRedis.AuthRedisProducer;
import com.example.notesWeb.service.AuthService;
import com.example.notesWeb.service.FailedException;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Autowired
    private AuthRedisProducer authRedisProducer;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.secure-cookie:false}")
    private boolean secureCookie;

    private void addRotationCookie(HttpServletResponse response, String rotationSecret) {
        ResponseCookie cookie = ResponseCookie.from("rotation_secret", rotationSecret)
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(604800)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    //Api Handle Register
    @Operation(summary = "User Register")
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
    @Operation(summary = "User Login")
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
    @Operation(summary = "Get the session ID for the user")
    @GetMapping("/login/result/{sessionId}")
    public ResponseEntity<?> getLoginResult(@PathVariable String sessionId, HttpServletResponse response){
        String key = "login:result:" + sessionId;
        Map<Object, Object> result = redisTemplate.opsForHash().entries(key);
        if(result == null || result.isEmpty()){
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(Map.of("status", "PENDING", "message", "Processing..."));
        }

        if ("SUCCESS".equalsIgnoreCase(String.valueOf(result.get("status")))) {
            //Get info from Redis
            String accessToken = (String) result.get("token");
            String rotationSecret = (String) result.get("rotationSecret"); //born from consumer

            //Set rotation secret into Cookie
            ResponseCookie cookie = ResponseCookie.from("rotation_secret", rotationSecret)
                    .httpOnly(true)
                    .secure(secureCookie) //only run through https
                    .path("/")
                    .maxAge(604800)
                    .sameSite("Lax")
                    .build();
            response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

            //delete key on Redis (one-time handshake)
            redisTemplate.delete(key);

            return ResponseEntity.ok(Map.of("status", "success", "accessToken", accessToken, "userID", result.get("id")));
        }
        return ResponseEntity.ok(result);
    }

    //Api handle refresh expired token & key
    @Operation(summary = "Refresh token secret for security")
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(name = "rotation_secret", required = false) String oldrs,
                                     HttpServletResponse response) {
        if (oldrs == null || oldrs.isEmpty()) {
            log.warn("Refresh failed: Cookie rotation_secret is missing");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Missing rotation secret");
        }

        log.info("Receive refresh request. Cooke present: {}", (oldrs != null));
        try {
            AuthResponse result = authService.refresh(oldrs);

            //Overwrite the new RS into the HttpOnly Cookie
            addRotationCookie(response, result.getRotationSecret());
            return ResponseEntity.ok(Map.of("accessToken", result.getToken(),"status", "SUCCESS"));
        } catch (FailedException e) {
            //If an error occurs, delete the client-side cookies immediately
            ResponseCookie deleteCookie = ResponseCookie.from("rotation_secret", "")
                    .maxAge(0).path("/").build();
            response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal error");
        }
    }

    @Operation(summary = "User logout")
    @PostMapping("/logout")
    public ResponseEntity<?> logOut (@CookieValue(name = "rotation_secret", required = false) String oldRs,
                                     HttpServletResponse response) {
        if (oldRs != null && !oldRs.isEmpty()) {
            authService.logOut(oldRs);
        }
        ResponseCookie deleteCookie  = ResponseCookie.from("rotation_secret", "")
                .httpOnly(true)
                .secure(secureCookie)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Logged out successfully. See you again!"));
    }
}
