package com.example.notesWeb.notes.messaging;
import com.example.notesWeb.notes.contract.NoteRequest;
import com.example.notesWeb.platform.redis.RedisStreamConsume;
import com.example.notesWeb.platform.messaging.FailureLedger;
import com.example.notesWeb.notes.application.CreateNoteService;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
@Component
public class NoteRedisConsumer extends RedisStreamConsume {
    private final CreateNoteService notes;
    public NoteRedisConsumer(RedisTemplate<String,Object> redis,CreateNoteService notes,FailureLedger failures) {
        super(redis,"notes:create:stream","notes-group",failures); this.notes=notes;
    }
    @Override protected void handleMessage(MapRecord<String,Object,Object> record) {
        var f=record.getValue();
        notes.createOnce("redis:notes:"+record.getId(),new NoteRequest((String)f.get("content"),(String)f.get("title")),(String)f.get("username"));
    }
}
