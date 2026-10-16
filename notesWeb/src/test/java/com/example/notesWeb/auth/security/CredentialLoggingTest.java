package com.example.notesWeb.auth.security;

import com.example.notesWeb.auth.contract.AuthRequest;
import com.example.notesWeb.auth.messaging.AuthRedisConsumer;
import com.example.notesWeb.auth.messaging.AuthRedisProducer;
import com.example.notesWeb.auth.application.AuthService;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.*;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import com.example.notesWeb.platform.messaging.FailureLedger;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith({OutputCaptureExtension.class, MockitoExtension.class})
class CredentialLoggingTest {
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) RedisTemplate<String, Object> redis;
    @Test void producerNeverLogsPassword(CapturedOutput output) {
        AuthRedisProducer producer = new AuthRedisProducer(redis);
        producer.sendLoginRequest(new AuthRequest("alice", "CANARY_password_287", "request-1"));
        assertThat(output.getAll()).doesNotContain("CANARY_password_287");
    }
    @Test void failedConsumerNeverLogsRecordOrExceptionPayload(CapturedOutput output) {
        AuthService auth = mock(AuthService.class);
        when(auth.login(any())).thenThrow(new RuntimeException("CANARY_password_287"));
        var results = mock(com.example.notesWeb.auth.messaging.AuthResults.class);
        when(results.claim("request-1")).thenReturn("lease");
        var consumer = new AuthRedisConsumer(redis, auth, results, mock(FailureLedger.class));
        var record = StreamRecords.mapBacked(Map.of("username", "alice", "password", "CANARY_password_287", "sessionId", "request-1"))
                .withStreamKey("auth:login:stream");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(consumer, "handleMessage", record)).isInstanceOf(IllegalStateException.class);
        assertThat(output.getAll()).doesNotContain("CANARY_password_287");
    }
}
