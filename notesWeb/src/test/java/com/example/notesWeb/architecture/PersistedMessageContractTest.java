package com.example.notesWeb.architecture;

import com.example.notesWeb.notes.contract.NoteUpdateEvent;
import com.example.notesWeb.todos.reminder.contract.ReminderDelay;
import com.example.notesWeb.platform.redis.RedisConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PersistedMessageContractTest {
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test void preRefactorNoteCommandReplaysWithoutChangingIdentityOrPayload() throws Exception {
        String payload="""
                {"eventId":"11111111-1111-4111-8111-111111111111",
                 "noteID":"22222222-2222-4222-8222-222222222222",
                 "userID":"33333333-3333-4333-8333-333333333333",
                 "username":"contract-fixture","noteRequest":{"content":"original body","title":"original title"}}
                """;
        var event=json.readValue(payload,NoteUpdateEvent.class);
        assertThat(event.getEventId()).isEqualTo(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        assertThat(json.readTree(json.writeValueAsBytes(event))).isEqualTo(json.readTree(payload));
    }
    @Test void reminderSupportsExistingTriggerAndLegacyTodoOnlyPayloads() throws Exception {
        String payload="""
                {"todoID":"22222222-2222-4222-8222-222222222222","trigger":"2026-10-16T00:00:00.123456Z"}
                """;
        var event=json.readValue(payload,ReminderDelay.class);
        assertThat(event.getTrigger()).isEqualTo(Instant.parse("2026-10-16T00:00:00.123456Z"));
        assertThat(json.readTree(json.writeValueAsBytes(event))).isEqualTo(json.readTree(payload));
        var legacy=json.readValue("{\"todoID\":\"22222222-2222-4222-8222-222222222222\"}",ReminderDelay.class);
        assertThat(legacy.getTodoID()).isEqualTo(event.getTodoID());
        assertThat(legacy.getTrigger()).isNull();
    }
    @Test void existingRedisSessionAndStreamStringsKeepTheirByteRepresentation() {
        var template=new RedisConfig().redisTemplate(mock(LettuceConnectionFactory.class));
        for(var configured:java.util.List.of(template.getValueSerializer(),template.getHashValueSerializer())) {
            assertThat(configured).isInstanceOf(GenericJackson2JsonRedisSerializer.class);
            var serializer=(GenericJackson2JsonRedisSerializer)configured;
            for(String value:java.util.List.of("Active","contract-fixture","11111111-1111-4111-8111-111111111111")) {
                byte[] legacy=("\""+value+"\"").getBytes(StandardCharsets.UTF_8);
                assertThat(serializer.deserialize(legacy)).isEqualTo(value);
                assertThat(serializer.serialize(value)).containsExactly(legacy);
            }
        }
    }
}
