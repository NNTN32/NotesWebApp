package com.example.notesWeb.exception.kafka;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import com.example.notesWeb.service.takeNotes.TaskNoteService;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class kafkaNoteConsumer {
    private final TaskNoteService notes;
    @KafkaListener(topics = "note-updates", groupId = "note-update-group")
    public void consume(ConsumerRecord<String, NoteUpdateEvent> record, Acknowledgment ack) {
        NoteUpdateEvent event = record.value();
        String key = event.getEventId() == null
                ? "kafka:"+record.topic()+":"+record.partition()+":"+record.offset()
                : "note-update:"+event.getEventId();
        notes.updateOnce(key, event); // proxy returns only after commit; exceptions reach bounded error handler
        ack.acknowledge();
    }
}
