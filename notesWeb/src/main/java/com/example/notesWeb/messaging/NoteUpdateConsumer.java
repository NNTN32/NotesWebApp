package com.example.notesWeb.messaging;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
@RequiredArgsConstructor
public class NoteUpdateConsumer {
    private final TaskNoteService notes;
    private final FailureLedger failures;
    private final ObjectMapper json;
    private final JdbcTemplate jdbc;

    @RabbitListener(queues="notes.note-updates.v1", containerFactory="noteUpdateListenerFactory", ackMode="MANUAL", concurrency="1")
    public void consume(Message raw, Channel channel) throws Exception {
        long tag=raw.getMessageProperties().getDeliveryTag();
        String payload=new String(raw.getBody(),StandardCharsets.UTF_8);
        String key="note-update:invalid:"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBody()));
        String deliveryId=raw.getMessageProperties().getMessageId();
        if (deliveryId==null) deliveryId=key;
        String noteId="invalid";
        boolean retry=false;
        try {
            NoteUpdateEvent event=json.readValue(payload,NoteUpdateEvent.class);
            if (event!=null && event.getEventId()!=null) key="note-update:"+event.getEventId();
            if (event!=null && event.getNoteID()!=null) noteId=event.getNoteID().toString();
            if (event==null || event.getEventId()==null || event.getNoteID()==null || event.getUserID()==null
                    || event.getUsername()==null || event.getUsername().isBlank() || event.getNoteRequest()==null)
                throw new IllegalArgumentException("Incomplete note command");
            boolean terminal=Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM message_dead_letter WHERE message_key=?)",Boolean.class,key));
            // Redelivery order can vary by broker version; never apply a later edit first.
            boolean blocked=Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM message_outbox command
                    JOIN message_outbox earlier ON earlier.destination=command.destination
                      AND earlier.routing_key=command.routing_key AND earlier.sequence_id<command.sequence_id
                    WHERE command.id=? AND earlier.kind='NOTE_UPDATE' AND earlier.destination='notes.note-updates.v1'
                      AND NOT EXISTS (SELECT 1 FROM message_inbox WHERE message_key='note-update:'||earlier.id)
                      AND NOT EXISTS (SELECT 1 FROM message_dead_letter WHERE message_key='note-update:'||earlier.id))
                    """,Boolean.class,event.getEventId()));
            if (!terminal) {
                if (blocked) {
                    try { failures.deferNote(payload,noteId,deliveryId); }
                    catch (Exception retentionFailure) { retry=true; }
                }
                else notes.updateOnce(key,event);
            }
        } catch (Exception failure) {
            try { failures.noteFailure(key,payload,noteId,deliveryId); }
            catch (Exception retentionFailure) { retry=true; }
        }
        if (retry) requeue(channel,tag);
        else channel.basicAck(tag,false);
    }

    private void requeue(Channel channel,long tag) throws Exception {
        Thread.sleep(250); // Avoid a hot redelivery loop during an infrastructure outage.
        channel.basicNack(tag,false,true);
    }
}
