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

    private final RateLimiter limitRequestLogin = RateLimiter.create(50.0);


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

    @Override
    protected void handleMessage(MapRecord<String, Object, Object> record) {
        Map<Object, Object> r = record.getValue();

        String username = (String) r.get("username");
        String password = (String) r.get("password");
        String sessionId = (String) r.get("sessionId");

        String resultKey = "login:result:" + sessionId;
        //Idempotent check
        if (Boolean.TRUE.equals(redisTemplate.hasKey(resultKey))) {
            log.info("Login result already exists for session {}", sessionId);
            return;
        }

        //Rate limit request
        if (!limitRequestLogin.tryAcquire()) {
            log.warn("Login rate limited {}", record.getId());

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/login-result",
                    new AuthResponse(null, null, username, null, Status.FAIL, "Too many login attempts")
            );
            return;
        }

        try {
            AuthResponse authResponse = authService.login(new AuthRequest(username, password, sessionId));

            messagingTemplate.convertAndSendToUser(
                    sessionId,
                    "/queue/login-result",
                    authResponse
            );

            redisTemplate.opsForHash().putAll(
                    resultKey,
                    Map.of(
                            "status", authResponse.getStatus().toString(),
                            "username", authResponse.getUsername(),
                            "token", authResponse.getToken(),
                            "id", String.valueOf(authResponse.getId()),
                            "role", String.valueOf(authResponse.getRole()),
                            "message", authResponse.getMessage()
                    )
            );

            redisTemplate.expire(resultKey, Duration.ofMinutes(1));
            //Catch same exception so it will log error for wrong password
            //Without discrimination auth fail & system fail
        }
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