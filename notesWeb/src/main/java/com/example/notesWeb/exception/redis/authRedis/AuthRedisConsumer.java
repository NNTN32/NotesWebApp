package com.example.notesWeb.exception.redis.authRedis;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.dtos.AuthResponse;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.model.Status;
import com.example.notesWeb.service.FailedException;
import com.example.notesWeb.service.AuthService;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.stream.MapRecord;
import java.time.Duration;
import java.util.*;

@Slf4j
@Component
public class AuthRedisConsumer extends RedisStreamConsume {
//    @Autowired
//    private RedisTemplate<String, Object> redisTemplate;

    private static final String STREAM_KEY = "auth:login:stream";
    private static final String GROUP = "auth-group";

    private final AuthService authService;
    private final SimpMessagingTemplate messagingTemplate;

//    //Using threadpool handle multiple login requests in parallel
//    private final ThreadPoolExecutor executorService = new ThreadPoolExecutor(
//            10,                              // corePoolSize: 10 main threads
//            30,                                         // maxPoolSize: up to 30 threads at high load
//            60L,                                        // idle thread timeout
//            TimeUnit.SECONDS,
//            new LinkedBlockingQueue<>(200),    //Queue contains 200 requests waiting to be processed
//            new ThreadPoolExecutor.CallerRunsPolicy()  //If queue is full then run on main thread
//    );

    private final RateLimiter limitRequestLogin = RateLimiter.create(50.0);

    //Semaphore for handle limit speed request
//    private final Semaphore rateLimiter = new Semaphore(50); //Commit maximize 50 request/sec

    //Write handle constructor instead of using bean let it created
    public AuthRedisConsumer(
            RedisTemplate<String, Object> redisTemplate,
            AuthService authService,
            SimpMessagingTemplate messagingTemplate
    ) {
        super(redisTemplate, STREAM_KEY, GROUP, 20);
        this.authService = authService;
        this.messagingTemplate = messagingTemplate;
    }

    //Logic handle read, caching redis
//    @PostConstruct
//    public void startConsumer(String consumerAuth){
//        try{
//            //Create stream group can redis request caching
//            redisTemplate.opsForStream().createGroup(STREAM_KEY, ReadOffset.latest(), GROUP);
//            log.info("Redis stream group '{}' created successfully", GROUP);
//        }catch (Exception e){
//            log.info("Redis stream group '{}' already exists", GROUP);
//        }
//
//        //Create thread of stream can read request message from client
//        Executors.newSingleThreadExecutor().submit(() -> {
//            log.info("AuthRedisConsumer started, waiting for login requests...", consumerAuth);
//            while (true){
//                try{
//                    limitRequestLogin.acquire();
//                    List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream()
//                            .read(Consumer.from(GROUP, consumerAuth),
//                                    //Read about 1s each 10 times request message
//                                    StreamReadOptions.empty().count(10).block(Duration.ofSeconds(1)),
//                                    //Read the last message caching not yet handle request
//                                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));
//
//                    if(records == null || records.isEmpty()) continue;
//
//                    for(MapRecord<String, Object, Object> record : records){
//                        executorService.submit(() -> handleAuthRecord(record));
//                    }
//                } catch (Exception e) {
//                    System.err.println("Stream read error: " + e.getMessage());
//                }
//            }
//        });
//    }

    //Method handle safe each record
//    private void proccessRecordSafely(MapRecord<String, Object, Object> record){
//        boolean acquired = false;
//        try{
//            //Only confirm max 50 request parallel processing
//            if (!rateLimiter.tryAcquire(5, TimeUnit.SECONDS)){
//                log.warn("Too many concurrent login requests. Delaying record {}", record.getId());
//                return;
//            }
//            acquired = true;
//            handleAuthRecord(record);
//        }catch (Exception e){
//            handleError(record, e);
//        }finally {
//            if(acquired) rateLimiter.release();
//        }
//    }

    //Logic handle message request Login from client
//    private void handleAuthRecord(MapRecord<String, Object, Object> record){
//        boolean permit = rateLimiter.tryAcquire();
//        if(!permit) {
//            System.err.println("Too many concurrent logins - rate limited.");
//            return;
//        }
//
//        try {
//            String username = (String) record.getValue().get("username");
//            String password = (String) record.getValue().get("password");
//            String sessionId = (String) record.getValue().get("sessionId");
//
//            try{
//                AuthRequest authRequest = new AuthRequest(username, password, sessionId);
//                AuthResponse authResponse = authService.login(authRequest);
//
//                messagingTemplate.convertAndSendToUser(sessionId, "/queue/login-result", authResponse);
//
//                //Save login result to Redis
//                String resultKey = "login:result:" + sessionId;
//                Map<String, String> resultMap = new HashMap<>();
//                resultMap.put("status" ,authResponse.getStatus().toString());
//                resultMap.put("username", authResponse.getUsername());
//                resultMap.put("token", authResponse.getToken());
//                resultMap.put("id", String.valueOf(authResponse.getId()));
//                resultMap.put("role", String.valueOf(authResponse.getRole()));
//                resultMap.put("message", authResponse.getMessage());
//
//                redisTemplate.opsForHash().putAll(resultKey, resultMap);
//                redisTemplate.expire(resultKey, Duration.ofMinutes(1));
//            } catch (Exception e) {
//                handleError(record, e);
//            }finally {
//                redisTemplate.opsForStream().acknowledge(STREAM_KEY, GROUP, record.getId());
//            }
//        }finally {
//            rateLimiter.release();
//        }
//    }

    //Logic handle error request login
//    private void handleError (MapRecord<String, Object, Object> record, Exception e){
//        String username = (String) record.getValue().get("username");
//        String sessionId = (String) record.getValue().get("sessionId");
//
//        AuthResponse errorResponse = new AuthResponse(
//                null, null, username, null, Status.FAIL, e.getMessage()
//        );
//        messagingTemplate.convertAndSendToUser(sessionId, "/queue/login-result", errorResponse);
//
//        String resultKey = "login:result:" + sessionId;
//        Map<String, String> resultMap = new HashMap<>();
//        resultMap.put("status", Status.FAIL.toString());
//        resultMap.put("username", username);
//        resultMap.put("token", "");
//        resultMap.put("id", "");
//        resultMap.put("role", "");
//        resultMap.put("message", e.getMessage());
//
//        redisTemplate.opsForHash().putAll(resultKey, resultMap);
//        redisTemplate.expire(resultKey, Duration.ofMinutes(1));
//    }

    @Override
    protected void handleMessage(
            MapRecord<String, Object, Object> record) {
        if (!limitRequestLogin.tryAcquire()) {
            log.warn("Login rate limited {}", record.getId());
            return;
        }

        Map<Object, Object> r = record.getValue();

        String username = (String) r.get("username");
        String password = (String) r.get("password");
        String sessionId = (String) r.get("sessionId");

        try {
            AuthResponse authResponse = authService.login(new AuthRequest(username, password, sessionId));

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/login-result",
                    authResponse
            );

            redisTemplate.opsForHash().putAll(
                    "login:result:" + sessionId,
                    Map.of(
                            "status", authResponse.getStatus().toString(),
                            "username", authResponse.getUsername(),
                            "token", authResponse.getToken(),
                            "id", String.valueOf(authResponse.getId()),
                            "role", String.valueOf(authResponse.getRole()),
                            "message", authResponse.getMessage()
                    )
            );

            redisTemplate.expire(
                    "login:result:" + sessionId,
                    Duration.ofMinutes(1)
            );
            //Catch same exception so it will log error for wrong password
            //Without discrimination auth fail & system fail
        }
//        catch (Exception e) {
//            log.error("Authentication failed {}", record.getId(), e);
//            messagingTemplate.convertAndSendToUser(
//                    sessionId,
//                    "/queue/login-result",
//                    new AuthResponse(null, null, username, null, Status.FAIL, e.getMessage())
//            );
//        }
        catch (FailedException e) {
            log.warn("Auth failed for user {} - {}", username, e.getMessage());

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/login-result",
                    new AuthResponse(null, null, username, null, Status.FAIL, e.getMessage())
            );
        }catch (Exception e) {
            log.error("System error while auth {}", record.getId(), e);
            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/login-result",
                    new AuthResponse(null, null, username, null, Status.FAIL, "Internal server error")
            );
        }
    }

}