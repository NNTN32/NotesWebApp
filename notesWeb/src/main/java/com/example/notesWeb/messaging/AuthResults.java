package com.example.notesWeb.messaging;
import com.example.notesWeb.dtos.AuthResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.*;
@Service
@RequiredArgsConstructor
public class AuthResults {
    private final RedisTemplate<String,Object> redis;
    public boolean completed(String id) { return Boolean.TRUE.equals(redis.hasKey("login:completed:"+id)); }
    public String claim(String id) {
        String lease=UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("login:lease:"+id,lease,Duration.ofSeconds(60))))
            throw new IllegalStateException("Authentication request busy");
        return lease;
    }
    public void complete(String id,String lease,AuthResponse result) {
        Long written=redis.execute(new DefaultRedisScript<>("""
                if redis.call('GET',KEYS[3]) ~= ARGV[8] then return 0 end
                redis.call('HSET',KEYS[1],'status',ARGV[1],'token',ARGV[2],'rotationSecret',ARGV[3],
                  'id',ARGV[4],'role',ARGV[5],'message',ARGV[6])
                redis.call('EXPIRE',KEYS[1],120)
                redis.call('SET',KEYS[2],ARGV[7],'EX',86400)
                return 1
                """,Long.class),List.of("login:result:"+id,"login:completed:"+id,"login:lease:"+id),
                result.getStatus().name(),Objects.toString(result.getToken(),""),Objects.toString(result.getRotationSecret(),""),
                Objects.toString(result.getId(),""),Objects.toString(result.getRole(),""),result.getMessage(),"DONE",lease);
        if (!Long.valueOf(1).equals(written)) throw new IllegalStateException("Authentication lease expired");
    }
    public void release(String id,String lease) {
        redis.execute(new DefaultRedisScript<>("if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end return 0",Long.class),List.of("login:lease:"+id),lease);
    }
}
