package com.example.notesWeb.notes.media.messaging;
import com.example.notesWeb.platform.redis.RedisStreamConsume;
import com.example.notesWeb.platform.messaging.FailureLedger;
import com.example.notesWeb.notes.media.application.MediaNoteService;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class MediaRedisConsumer extends RedisStreamConsume {
    private final MediaNoteService media;
    public MediaRedisConsumer(RedisTemplate<String,Object> redis,MediaNoteService media,FailureLedger failures) {
        super(redis,"media:create:stream","media-group",failures); this.media=media;
    }
    @Override protected void handleMessage(MapRecord<String,Object,Object> record) {
        var f=record.getValue();
        media.saveOnce("redis:media:"+UUID.fromString(f.get("requestId").toString()),UUID.fromString(f.get("postID").toString()),f.get("finalUrl").toString(),f.get("resourceType").toString());
    }
}
