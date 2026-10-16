package com.example.notesWeb.exception;
import com.example.notesWeb.messaging.FailureLedger;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
@Slf4j
public abstract class RedisStreamConsume {
    protected final RedisTemplate<String,Object> redisTemplate;
    protected final String streamKey;
    protected final String group;
    private final FailureLedger failures;
    private final AtomicBoolean started=new AtomicBoolean();
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile boolean running=true;
    private String pendingCursor="0-0";
    protected RedisStreamConsume(RedisTemplate<String,Object> redis,String stream,String group,FailureLedger failures) {
        this.redisTemplate=redis; this.streamKey=stream; this.group=group; this.failures=failures;
    }
    protected abstract void handleMessage(MapRecord<String,Object,Object> record);
    public void start(String name) { if (started.compareAndSet(false,true)) executor.submit(()->loop(name)); }
    private void loop(String name) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                recoverPending(name);
                var records=redisTemplate.<Object,Object>boundStreamOps(streamKey).read(Consumer.from(group,name),StreamReadOptions.empty().count(10).block(Duration.ofSeconds(2)),ReadOffset.lastConsumed());
                if (records!=null) for(var record:records) processSafely(record);
            } catch (RuntimeException failure) {
                log.warn("Redis stream unavailable [{}]; messages retained",streamKey);
                try { Thread.sleep(1000); } catch(InterruptedException stop) { Thread.currentThread().interrupt(); }
            }
        }
    }
    protected void processSafely(MapRecord<String,Object,Object> record) {
        try { handleMessage(record); }
        catch (Exception processingFailure) {
            try { if (!failures.redisFailure("redis:"+streamKey+":"+record.getId(),streamKey,record.getId().getValue())) return; }
            catch (Exception retentionFailure) { log.error("Redis failure not retained [{}]; leaving pending",streamKey); return; }
        }
        redisTemplate.opsForStream().acknowledge(streamKey,group,record.getId());
    }
    protected void recoverPending(String name) {
        var page=redisTemplate.opsForStream().pending(streamKey,group,Range.rightUnbounded(Range.Bound.exclusive(pendingCursor)),20);
        if (page==null || page.isEmpty()) { pendingCursor="0-0"; return; }
        for(var pending:page) {
            pendingCursor=pending.getId().getValue();
            if (pending.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofSeconds(30))<0) continue;
            var claimed=redisTemplate.opsForStream().claim(streamKey,group,name,Duration.ofSeconds(30),pending.getId());
            if (claimed!=null) for(var record:claimed) processSafely(record);
        }
    }
    @PreDestroy public void shutdown() { running=false; executor.shutdownNow(); }
}
