package com.example.notesWeb.exception.redis.noteRedis;
import com.example.notesWeb.dtos.NoteDto.NoteRequest;
import com.example.notesWeb.exception.RedisStreamConsume;
import com.example.notesWeb.service.takeNotes.CreateNoteService;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
@Component
public class NoteRedisConsumer extends RedisStreamConsume {
    private final CreateNoteService notes;
    public NoteRedisConsumer(RedisTemplate<String,Object> redis,CreateNoteService notes) {
        super(redis,"notes:create:stream","notes-group",10); this.notes=notes;
    }
    @Override protected void handleMessage(MapRecord<String,Object,Object> record) {
        var f=record.getValue();
        notes.createOnce("redis:notes:"+record.getId(),new NoteRequest((String)f.get("content"),(String)f.get("title")),(String)f.get("username"));
    }
}
