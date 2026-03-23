package com.example.notesWeb.service;

import com.example.notesWeb.config.jwtProvider;
import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.model.Role;
import com.example.notesWeb.model.Status;
import com.example.notesWeb.model.User;
import com.example.notesWeb.repository.IdGenerateRepo;
import com.example.notesWeb.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional //Combine multiple DB operations into a single transaction.
public class AuthService {
    @Autowired
    private UserRepo userRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private jwtProvider tokenProvider;

    @Autowired
    private IdGenerateRepo idGenerateRepo;

    private final RedisTemplate<String, Object> redisTemplate;

    //Logic handle request register user
    public String register(AuthRequest authRequest){
        if(userRepo.existsByUsername(authRequest.getUsername())){
            throw new FailedException("Username has already existed!");
        }
        User user = new User();
        user.setId(idGenerateRepo.nextId());
        user.setUsername(authRequest.getUsername());
        user.setEmail(authRequest.getEmail());
        user.setPassword(passwordEncoder.encode(authRequest.getPassword()));
        user.setRole(authRequest.getRole() != null ? authRequest.getRole() : Role.USER);

        userRepo.save(user); //only commit when method ended OK <-> fail → rollback user
        return "User registered successfully";
    }

    //Logic handle request login user
    public AuthResponse login(AuthRequest request){
        User user = userRepo.findByUsername(request.getUsername())
                .orElseThrow(() -> new FailedException("Username not found!"));

        if(!passwordEncoder.matches(request.getPassword(), user.getPassword())){
            //Ok for Rest sync but wrong credential -> system async error bc
            // message can't without discrimination username/password so it will throw into Redis Consumer -> log error 
//            throw new IllegalArgumentException("Invalid username");
            throw new FailedException("Invalid password");
        }

        String token = tokenProvider.generateToken(user);
        String rs = tokenProvider.generateRSToken(user);
        redisTemplate.opsForValue().set("session:" + user.getUsername() + ":" + rs, "Active", Duration.ofDays(7));

        return new AuthResponse(
                token,
                rs,
                user.getId(),
                user.getUsername(),
                user.getRole(),
                Status.SUCCESS,
                "Login successful"
        );
    }

    //Logic tracking time refresh key token
    public AuthResponse refresh (String oldRS) {
        //get user from RS (even rs has expired from jwt)
        String username = tokenProvider.getUserFromJwt(oldRS);

        //find rs in Redis
        String redisKeyPattern = "session:" + username + ":" + oldRS;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(redisKeyPattern))) {
            //If the RS has a valid signature & it's NOT in Redis
            log.error("Suspected hacking! reuse for user: {}", username);

            //Delete all sessions for this user to force a full device logout
            Set<String> keys = redisTemplate.keys("session:" + username + ":*");
            if (keys != null) {
                redisTemplate.delete(keys);
            }
            throw new FailedException("Security alert : All session revoked. Please login again!");
        }

        //If valid -> DELETE old RS IMMEDIATELY (One-time use)
        redisTemplate.delete(redisKeyPattern);

        //Reborn new access token & rotation secret(refresh token)
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new FailedException("User not found!"));
        String newAccessToken = tokenProvider.generateToken(user);
        String newRS = tokenProvider.generateRSToken(user);
        //Save rs into Redis (TTL 7 days)
        String  newRedisKey = "session:" + username + ":" + newRS;
        redisTemplate.opsForValue().set(newRedisKey, "Active", Duration.ofDays(7));

        return new AuthResponse(newAccessToken, newRS, user.getId(), user.getUsername(), user.getRole(), Status.SUCCESS, "Token rotated");
    }
}
