package com.example.notesWeb.messaging;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.exception.realtime.timeEvent.messagePublish.ReminderQueueConsume;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DeliverySafetyTest {
    @Mock(answer = org.mockito.Answers.RETURNS_DEEP_STUBS) RedisTemplate<String, Object> redis;
    final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void rabbitFailureIsRetainedBeforeAckAndDbOutageRequeues() throws Exception {
        var delivery=mock(ReminderDelivery.class); var failures=mock(FailureLedger.class); var channel=mock(Channel.class);
        var listener=new ReminderQueueConsume(delivery,failures,json);
        var props=new MessageProperties(); props.setDeliveryTag(7);
        var message=new Message(("{\"todoID\":\""+UUID.randomUUID()+"\"}").getBytes(StandardCharsets.UTF_8),props);
        doThrow(new IllegalStateException("DB down")).when(delivery).deliver(any());
        listener.consumeReminder(message,channel);
        var order=inOrder(delivery,failures,channel);
        order.verify(delivery).deliver(any()); order.verify(failures).rabbitFailure(anyString(),anyString(),anyString()); order.verify(channel).basicAck(7,false);
        reset(channel);
        doThrow(new IllegalStateException("Cannot retain")).when(failures).rabbitFailure(anyString(),anyString(),anyString());
        listener.consumeReminder(message,channel);
        verify(channel).basicNack(7,false,true); verify(channel,never()).basicAck(anyLong(),anyBoolean());
    }
    @Test void malformedRabbitPayloadIsNotRejectedBeforeDurableFailureHandling() throws Exception {
        var failures=mock(FailureLedger.class); var channel=mock(Channel.class);
        var delivery=mock(ReminderDelivery.class);
        var listener=new ReminderQueueConsume(delivery,failures,json);
        var props=new MessageProperties(); props.setDeliveryTag(2);
        listener.consumeReminder(new Message("invalid-json".getBytes(StandardCharsets.UTF_8),props),channel);
        verifyNoInteractions(delivery);
        var order=inOrder(failures,channel); order.verify(failures).rabbitFailure(anyString(),eq("invalid-json"),anyString()); order.verify(channel).basicAck(2,false);
    }
    @Test void lostRabbitAckPropagatesForRedelivery() throws Exception {
        var channel=mock(Channel.class); var failures=mock(FailureLedger.class);
        doThrow(new java.io.IOException("Connection lost")).when(channel).basicAck(0,false);
        var listener=new ReminderQueueConsume(mock(ReminderDelivery.class),failures,json);
        assertThatThrownBy(()->listener.consumeReminder(new Message("{}".getBytes(StandardCharsets.UTF_8),new MessageProperties()),channel)).isInstanceOf(java.io.IOException.class);
        verifyNoInteractions(failures);
    }
    @Test void redisFailureOrUnavailableDlqNeverAcknowledges() {
        var ledger=mock(FailureLedger.class);
        var consumer=new RedisStreamConsume(redis,"stream","group",ledger) {
            @Override protected void handleMessage(MapRecord<String,Object,Object> record) { throw new IllegalStateException("DB failed"); }
        };
        var record=StreamRecords.newRecord().in("stream").ofMap(Map.<Object,Object>of("key","value")).withId(RecordId.of("1-0"));
        ReflectionTestUtils.invokeMethod(consumer,"processSafely",record);
        verify(redis.opsForStream(),never()).acknowledge(anyString(),anyString(),any(RecordId.class));
        doThrow(new IllegalStateException()).when(ledger).redisFailure(anyString(),anyString(),anyString());
        ReflectionTestUtils.invokeMethod(consumer,"processSafely",record);
        verify(redis.opsForStream(),never()).acknowledge(anyString(),anyString(),any(RecordId.class));
        consumer.shutdown();
    }
    @Test void redisExhaustedMessageIsAcknowledgedOnlyAfterFailureCommit() {
        var ledger=mock(FailureLedger.class); when(ledger.redisFailure(anyString(),anyString(),anyString())).thenReturn(true);
        var consumer=new RedisStreamConsume(redis,"stream","group",ledger) {
            @Override protected void handleMessage(MapRecord<String,Object,Object> record) { throw new IllegalArgumentException(); }
        };
        var record=StreamRecords.newRecord().in("stream").ofMap(Map.<Object,Object>of("key","value")).withId(RecordId.of("1-0"));
        ReflectionTestUtils.invokeMethod(consumer,"processSafely",record);
        var order=inOrder(ledger,redis.opsForStream()); order.verify(ledger).redisFailure(anyString(),anyString(),eq("1-0"));
        order.verify(redis.opsForStream()).acknowledge("stream","group",record.getId()); consumer.shutdown();
    }
    @Test void rabbitConfirmMustBeAckedAndRouted() throws Exception {
        var rabbit=mock(RabbitTemplate.class);
        var publisher=new ConfirmedPublisher(rabbit,mock(SimpMessagingTemplate.class),json);
        doAnswer(call->{ CorrelationData c=call.getArgument(3); c.getFuture().complete(new CorrelationData.Confirm(false,"nack")); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        assertThatThrownBy(()->publisher.send(UUID.randomUUID(),"RABBIT","events","ready","{}")).isInstanceOf(IllegalStateException.class);
        doAnswer(call->{ CorrelationData c=call.getArgument(3); c.setReturned(new ReturnedMessage(call.getArgument(2),312,"NO_ROUTE","events","missing"));
            c.getFuture().complete(new CorrelationData.Confirm(true,null)); return null; })
                .when(rabbit).send(anyString(),anyString(),any(Message.class),any(CorrelationData.class));
        assertThatThrownBy(()->publisher.send(UUID.randomUUID(),"RABBIT","events","missing","{}")).isInstanceOf(IllegalStateException.class);
    }
}
