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
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class AuthRedisConsumer {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    //Using threadpool handle multiple login requests in parallel
    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
            10,                              // corePoolSize: 10 main threads
            30,                                         // maxPoolSize: up to 30 threads at high load
            60L,                                        // idle thread timeout
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(200),    //Queue contains 200 requests waiting to be processed
            new ThreadPoolExecutor.CallerRunsPolicy()  //If queue is full then run on main thread
    );

    //Semaphore for handle limit speed request
    private final Semaphore rateLimiter = new Semaphore(20); //Commit maximize 20 request/sec

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
                try{
                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
                            .read(Consumer.from(GROUP, CONSUMER_NAME),
                                    //Read about 1s each 10 times request message
                                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
                                    //Read the last message caching not yet handle request
                                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

                    if(records == null || records.isEmpty()) continue;

                    for(MapRecord<String, Object, Object> record : records){
                        executorService.submit(() -> handleAuthRecord(record));
                    }
                } catch (Exception e) {
                    System.err.println("Stream read error: " + e.getMessage());
                }
            }
        });
    }

    //Logic handle message request Login from client
    private void handleAuthRecord(MapRecord<String, Object, Object> record){
        boolean permit = rateLimiter.tryAcquire();
        if(!permit) {
            System.err.println("Too many concurrent logins - rate limited.");
            return;
        }

        try {
            String username = (String) record.getValue().get("username");
            String password = (String) record.getValue().get("password");
            String sessionId = (String) record.getValue().get("sessionId");

            try{
                AuthRequest authRequest = new AuthRequest(username, password, sessionId);
                AuthResponse authResponse = authService.login(authRequest);

                messagingTemplate.convertAndSendToUser(sessionId, "/queue/login-result", authResponse);

                //Save login result to Redis
                String resultKey = "login:result:" + sessionId;
                Map<String, String> resultMap = new HashMap<>();
                resultMap.put("status" ,authResponse.getStatus().toString());
                resultMap.put("username", authResponse.getUsername());
                resultMap.put("token", authResponse.getToken());
                resultMap.put("id", String.valueOf(authResponse.getId()));
                resultMap.put("role", String.valueOf(authResponse.getRole()));
                resultMap.put("message", authResponse.getMessage());

                redisTemplate.opsForHash().putAll(resultKey, resultMap);
                redisTemplate.expire(resultKey, Duration.ofMinutes(1));
            } catch (Exception e) {
                handleError(record, e);
            }finally {
                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
            }
        }finally {
            rateLimiter.release();
        }
    }

    //Logic handle error request login
    private void handleError (MapRecord<String, Object, Object> record, Exception e){
        String username = (String) record.getValue().get("username");
        String sessionId = (String) record.getValue().get("sessionId");

        AuthResponse errorResponse = new AuthResponse(
                null, null, username, null, Status.FAIL, e.getMessage()
        );
        messagingTemplate.convertAndSendToUser(sessionId, "/queue/login-result", errorResponse);

        String resultKey = "login:result:" + sessionId;
        Map<String, String> resultMap = new HashMap<>();
        resultMap.put("status", Status.FAIL.toString());
        resultMap.put("username", username);
        resultMap.put("token", "");
        resultMap.put("id", "");
        resultMap.put("role", "");
        resultMap.put("message", e.getMessage());

        redisTemplate.opsForHash().putAll(resultKey, resultMap);
        redisTemplate.expire(resultKey, Duration.ofMinutes(1));
    }
}
