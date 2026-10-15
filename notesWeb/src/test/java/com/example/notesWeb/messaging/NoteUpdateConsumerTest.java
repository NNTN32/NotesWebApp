package com.example.notesWeb.messaging;

import com.example.notesWeb.dtos.NoteDto.*;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class NoteUpdateConsumerTest {
    final TaskNoteService notes=mock(TaskNoteService.class);
    final FailureLedger failures=mock(FailureLedger.class);
    final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    final Channel channel=mock(Channel.class);
    final NoteUpdateConsumer consumer=new NoteUpdateConsumer(notes,failures,json,jdbc);
    final NoteUpdateEvent event=new NoteUpdateEvent(UUID.randomUUID(),UUID.randomUUID(),"alice",new NoteRequest("body","title"));
    Message message() throws Exception {
        var properties=new MessageProperties(); properties.setDeliveryTag(7);
        return new Message(json.writeValueAsBytes(event),properties);
    }
    @Test void businessCommitPrecedesAckAndLostAckPropagates() throws Exception {
        consumer.consume(message(),channel);
        var order=inOrder(notes,channel); order.verify(notes).updateOnce("note-update:"+event.getEventId(),event);
        order.verify(channel).basicAck(7,false);
        doThrow(new java.io.IOException()).when(channel).basicAck(7,false);
        assertThatThrownBy(()->consumer.consume(message(),channel)).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(failures);
    }
    @Test void processingFailureIsRetainedBeforeAck() throws Exception {
        doThrow(new IllegalStateException()).when(notes).updateOnce(anyString(),any());
        consumer.consume(message(),channel);
        var order=inOrder(failures,channel);
        order.verify(failures).noteFailure(eq("note-update:"+event.getEventId()),anyString(),eq(event.getNoteID().toString()),anyString());
        order.verify(channel).basicAck(7,false);
    }
    @Test void failedRetentionNeverAcknowledges() throws Exception {
        doThrow(new IllegalStateException()).when(notes).updateOnce(anyString(),any());
        doThrow(new IllegalStateException()).when(failures).noteFailure(anyString(),anyString(),anyString(),anyString());
        consumer.consume(message(),channel);
        verify(channel).basicNack(7,false,true); verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
    @Test void malformedPayloadIsRetainedWithStableIdentity() throws Exception {
        var raw=new Message("broken-json".getBytes(java.nio.charset.StandardCharsets.UTF_8),new MessageProperties());
        consumer.consume(raw,channel); consumer.consume(raw,channel);
        var keys=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(failures,times(2)).noteFailure(keys.capture(),eq("broken-json"),eq("invalid"),anyString());
        assertThat(keys.getAllValues().getFirst()).isEqualTo(keys.getAllValues().getLast());
        verifyNoInteractions(notes); verify(channel,times(2)).basicAck(0,false);
    }
    @Test void missingIdentityCannotUpdateAndBlockedCommandDoesNotConsumeRetryBudget() throws Exception {
        event.setEventId(null); consumer.consume(message(),channel);
        verifyNoInteractions(notes);
        reset(channel,failures);
        event.setEventId(UUID.randomUUID());
        when(jdbc.queryForObject(contains("JOIN message_outbox"),eq(Boolean.class),eq(event.getEventId()))).thenReturn(true);
        consumer.consume(message(),channel);
        verifyNoInteractions(notes); verify(failures).deferNote(anyString(),eq(event.getNoteID().toString()),anyString());
        verify(failures,never()).noteFailure(anyString(),anyString(),anyString(),anyString()); verify(channel).basicAck(7,false);
        reset(channel);
        doThrow(new IllegalStateException()).when(failures).deferNote(anyString(),anyString(),anyString());
        consumer.consume(message(),channel);
        verify(channel).basicNack(7,false,true); verify(channel,never()).basicAck(anyLong(),anyBoolean());
        verify(failures,never()).noteFailure(anyString(),anyString(),anyString(),anyString());
    }
    @Test void terminalRedeliveryCannotApplyAnOlderEdit() throws Exception {
        when(jdbc.queryForObject(startsWith("SELECT EXISTS (SELECT 1 FROM message_dead_letter"),eq(Boolean.class),any(Object.class))).thenReturn(true);
        consumer.consume(message(),channel);
        verifyNoInteractions(notes,failures); verify(channel).basicAck(7,false);
    }
}
