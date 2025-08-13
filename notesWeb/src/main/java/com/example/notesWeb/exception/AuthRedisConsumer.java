package com.example.notesWeb.exception;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.model.Status;
import com.example.notesWeb.service.AuthService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
public class AuthRedisConsumer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    private static final String STREAM_KEY = "auth:login:stream";
    private static final String GROUP = "auth-group";
    private static final String CONSUMER_NAME = "consumer-1";

    //Logic handle read, caching redis
    @PostConstruct
    public void startConsumer(){
        try{
            //Create stream group can redis request caching
            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
        }catch (Exception ignored){}

        //Create thread of stream can read request message from client
        Executors.newSingleThreadExecutor().submit(() -> {
            while (true){
                List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                        .read(Consumer.from(GROUP, CONSUMER_NAME),
                                //Read about 1s each 10 times request message
                                StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                                //Read the last message caching not yet handle request
                                StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

                //Logic handle new message request from client
                if(records != null){
                    for(MapRecord<String, Object, Object> record : records){
                        String username = (String) record.getValue().get("username");
                        String password = (String) record.getValue().get("password");
                        String sessionId = (String) record.getValue().get("sessionId");

                        //Handle auth service taken data user
                        try{
                            AuthRequest request = new AuthRequest(username, password, sessionId);
                            AuthResponse response = authService.login(request);

                            messagingTemplate.convertAndSendToUser(sessionId, "/queue/login-result", response);

                            //Save result into redis hash for service & api can read, handle
                            String resultKey = "login:result:" + sessionId;

                            //Using HashMap class Dto
                            Map<String, String> resultMap = new HashMap<>();
                            resultMap.put("status", response.getStatus().toString());
                            resultMap.put("username", response.getUsername());
                            resultMap.put("token", response.getToken());
                            resultMap.put("id", String.valueOf(response.getId()));
                            resultMap.put("role", String.valueOf(response.getRole()));
                            resultMap.put("message", response.getMessage());

                            redisTemplate.opsForHash().putAll(resultKey, resultMap);
                            //Setting time expired for redis
                            redisTemplate.expire(resultKey,Duration.ofMinutes(5));
                        } catch (Exception e) {
                            AuthResponse errorResponse = new AuthResponse(
                                    null,null, username, null, Status.FAIL, e.getMessage());

                            messagingTemplate.convertAndSendToUser(sessionId, "queue/login-result", errorResponse);

                            //Save fail result into Redis
                            String resultKey = "login:result" + sessionId;
                            Map<String, String> resultMap = new HashMap<>();
                            resultMap.put("status", Status.FAIL.toString());
                            resultMap.put("username", username);
                            resultMap.put("token", "");
                            resultMap.put("id", "");
                            resultMap.put("role", "");
                            resultMap.put("message", e.getMessage());

                            redisTemplate.opsForHash().putAll(resultKey, resultMap);
                            redisTemplate.expire(resultKey, Duration.ofMinutes(5));

                        }
                        redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
                    }
                }
            }
        });
    }
}
