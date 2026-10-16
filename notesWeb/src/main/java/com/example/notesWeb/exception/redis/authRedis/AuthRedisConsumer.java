package com.example.notesWeb.exception.redis.authRedis;
import com.example.notesWeb.dtos.*;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.messaging.AuthResults;
import com.example.notesWeb.model.Status;
import com.example.notesWeb.service.*;
import com.google.common.util.concurrent.RateLimiter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.MapRecord;
import com.example.notesWeb.messaging.FailureLedger;
import org.springframework.stereotype.Component;
@Component
public class AuthRedisConsumer extends RedisStreamConsume {
    private final AuthService authService;
    private final AuthResults results;
    private final RateLimiter rate=RateLimiter.create(50.0);
    public AuthRedisConsumer(RedisTemplate<String,Object> redis,AuthService auth,AuthResults results,FailureLedger failures) {
        super(redis,"auth:login:stream","auth-group",failures); this.authService=auth; this.results=results;
    }
    @Override protected void handleMessage(MapRecord<String,Object,Object> record) {
        var fields=record.getValue();
        String username=(String)fields.get("username"), password=(String)fields.get("password"), id=(String)fields.get("sessionId");
        if (username==null || password==null || id==null) throw new IllegalArgumentException("Invalid authentication request");
        if (results.completed(id)) return;
        String lease=results.claim(id);
        try {
            if (results.completed(id)) return;
            AuthResponse result;
            try {
                if (!rate.tryAcquire()) throw new FailedException("Rate limited");
                result=authService.login(new AuthRequest(username,password,id));
            } catch (FailedException invalidCredentials) {
                result=new AuthResponse(null,null,null,username,null,Status.FAIL,"Invalid credentials or too many attempts");
            } catch (RuntimeException infrastructureFailure) {
                // Do not propagate arbitrary credential-bearing exception messages into logs/DLQ.
                throw new IllegalStateException("Authentication processing failed");
            }
            results.complete(id,lease,result); // atomic result + TTL + processed marker; no WebSocket dependency
        } finally { results.release(id,lease); }
    }
}
