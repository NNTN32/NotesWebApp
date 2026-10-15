package com.example.notesWeb.security;

import com.example.notesWeb.dtos.AuthRequest;
import com.example.notesWeb.exception.redis.authRedis.*;
import com.example.notesWeb.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class CredentialLoggingTest {
    @Test void producerNeverLogsPassword(CapturedOutput output) {
        RedisTemplate redis = mock(RedisTemplate.class, RETURNS_DEEP_STUBS);
        AuthRedisProducer producer = new AuthRedisProducer();
        ReflectionTestUtils.setField(producer, "redisTemplate", redis);
        producer.sendLoginRequest(new AuthRequest("alice", "CANARY_password_287", "request-1"));
        assertThat(output.getAll()).doesNotContain("CANARY_password_287");
    }
    @Test void failedConsumerNeverLogsRecordOrExceptionPayload(CapturedOutput output) {
        RedisTemplate redis = mock(RedisTemplate.class);
        AuthService auth = mock(AuthService.class);
        when(auth.login(any())).thenThrow(new RuntimeException("CANARY_password_287"));
        var consumer = new AuthRedisConsumer(redis, auth, mock(SimpMessagingTemplate.class));
        var results = mock(com.example.notesWeb.messaging.AuthResults.class);
        when(results.claim("request-1")).thenReturn("lease");
        ReflectionTestUtils.setField(consumer, "results", results);
        var record = StreamRecords.mapBacked(Map.of("username", "alice", "password", "CANARY_password_287", "sessionId", "request-1"))
                .withStreamKey("auth:login:stream");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "handleMessage", record)).isInstanceOf(IllegalStateException.class);
        assertThat(output.getAll()).doesNotContain("CANARY_password_287");
    }
}
