package com.example.notesWeb.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.Thread.sleep;

@Slf4j
public abstract class RedisStreamConsume {
    protected final RedisTemplate<String, Object> redisTemplate;
    protected final ExecutorService executorService;
    protected final String streamKey;
    protected final String group;

    protected volatile boolean running = true;

    protected RedisStreamConsume(
            RedisTemplate<String, Object> redisTemplate,
            String streamKey,
            String group,
            int workerThreads
    ){
        this.redisTemplate = redisTemplate;
        this.streamKey = streamKey;
        this.group = group;
        this.executorService = Executors.newFixedThreadPool(workerThreads);
    }

    //For implement of another class like Redis Consumer
    protected abstract void handleMessage(MapRecord<String, Object, Object> record);

    public void start(String consumerName) {
//        createGroup();

        recoverPending(consumerName);
        new Thread(() -> consumeLoop(consumerName),
                "redis-consumer-" + consumerName).start();
    }

//    private void createGroup() {
//        try{
//            redisTemplate.opsForStream()
//                    .createGroup(streamKey, ReadOffset.latest(), group);
//            log.info("Group [{}] created for stream [{}]", group, streamKey);
//        }catch (RedisBusyException e) {
//            log.info("Group [{}] already existed", group);
//        }
//    }

    private void consumeLoop(String consumerName) {
        log.info("Started consumer [{}] for stream [{}]", consumerName, streamKey);

        while (running) {
            try{
                //Redis send message for consumerName
                List<MapRecord<String, Object, Object>> records =
                        redisTemplate.opsForStream().read(
                          Consumer.from(group, consumerName),
                          StreamReadOptions.empty().count(20).block(Duration.ofSeconds(2)),
                          StreamOffset.create(streamKey, ReadOffset.lastConsumed())
                        );

                if (records == null || records.isEmpty()) continue;

                records.forEach(record ->
                        //split consume of business processing
                        executorService.submit(() -> processSafely(record))
                );
            }catch (Exception e){
                log.error("Redis stream error [{}]: {}", streamKey, e.getMessage());
                sleep(2000);
            }
        }
    }

    private void processSafely(MapRecord<String, Object, Object> record) {
        try{
            handleMessage(record);
            redisTemplate.opsForStream()
                    .acknowledge(streamKey, group, record.getId());
        }catch (Exception e) {
            log.error("Failed to process record {}: {}", record.getId(), e.getMessage(), e);
        }
    }

    //Method check process message when app crash/restart
    private void recoverPending (String consumerName) {
        try{
            List<MapRecord<String, Object, Object>> pending =
                    redisTemplate.opsForStream().read(
                            Consumer.from(group, consumerName),
                            StreamReadOptions.empty().count(20),
                            StreamOffset.create(streamKey, ReadOffset.from("0"))
                    );

            if (pending != null && !pending.isEmpty()) {
                log.info("Recovering {} pending message from {}", pending.size(), streamKey);
                pending.forEach(r -> executorService.submit(() -> processSafely(r)));
            }
        } catch (Exception e) {
            log.warn("No pending message to recover for {}", streamKey);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
