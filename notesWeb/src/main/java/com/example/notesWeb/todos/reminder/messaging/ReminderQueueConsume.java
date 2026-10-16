package com.example.notesWeb.todos.reminder.messaging;
import com.example.notesWeb.platform.messaging.FailureLedger;
import com.example.notesWeb.todos.reminder.application.ReminderDelivery;
import com.example.notesWeb.todos.reminder.contract.ReminderDelay;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
@Service
@RequiredArgsConstructor
public class ReminderQueueConsume {
    private final ReminderDelivery delivery;
    private final FailureLedger failures;
    private final ObjectMapper json;
    @RabbitListener(queues={"notes.reminder.v2","reminder.mg.queue"},ackMode="MANUAL",concurrency="1")
    public void consumeReminder(Message raw,Channel channel) throws Exception {
        long tag=raw.getMessageProperties().getDeliveryTag();
        String payload=new String(raw.getBody(),StandardCharsets.UTF_8);
        String key="rabbit:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBody()));
        try { delivery.deliver(json.readValue(payload,ReminderDelay.class)); }
        catch (Exception failure) {
            try { failures.rabbitFailure(key,payload,"Reminder processing failed"); }
            catch (Exception cannotRetain) { channel.basicNack(tag,false,true); return; }
        }
        // Business transaction or durable retry/DLQ has committed before ACK.
        channel.basicAck(tag,false);
    }
}
