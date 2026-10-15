package com.example.notesWeb.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.ErrorResponseException;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class ApiRateLimitIntegrationTest {
    @Test void atomicBucketsSurviveNewInstancesRefillExpireAndIsolateClients() throws Exception {
        int port = Integer.parseInt(Objects.requireNonNull(System.getenv("MESSAGING_TEST_REDIS_PORT")));
        var factory = new LettuceConnectionFactory("localhost", port);
        factory.afterPropertiesSet(); factory.start();
        var redis = new StringRedisTemplate(factory);
        String budget = "test-"+UUID.randomUUID();
        var limiter = new ApiRateLimiter(redis, "", true);
        var pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> calls = new ArrayList<>();
            for (int i=0;i<20;i++) calls.add(() -> {
                try { limiter.check(budget, "same-client", 5, 300); return true; }
                catch (ErrorResponseException e) { assertThat(e.getStatusCode().value()).isEqualTo(429); return false; }
            });
            long accepted = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) accepted++;
            assertThat(accepted).isEqualTo(5);
            assertThatThrownBy(() -> new ApiRateLimiter(redis,"",true).check(budget,"same-client",5,300))
                    .isInstanceOf(ErrorResponseException.class);
            limiter.check(budget,"another-client",5,300);
            limiter.check(budget,"refill",1,1);
            assertThatThrownBy(() -> limiter.check(budget,"refill",1,1)).isInstanceOf(ErrorResponseException.class);
            String key="auth:rate-limit:"+budget+":"+DigestUtils.sha256Hex("refill");
            assertThat(redis.getExpire(key,TimeUnit.MILLISECONDS)).isBetween(1L,1000L);
            Thread.sleep(1100);
            limiter.check(budget,"refill",1,1);
        } finally {
            pool.shutdownNow();
            for (String id : List.of("same-client","another-client","refill"))
                redis.delete("auth:rate-limit:"+budget+":"+DigestUtils.sha256Hex(id));
            factory.destroy();
        }
    }
}
