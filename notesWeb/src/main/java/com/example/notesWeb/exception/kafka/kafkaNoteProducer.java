package com.example.notesWeb.exception.kafka;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class kafkaNoteProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendNoteUpdate(NoteUpdateEvent noteEvent) {
        kafkaTemplate.send("note-updates", noteEvent.getNoteID().toString(), noteEvent);
    }
}
