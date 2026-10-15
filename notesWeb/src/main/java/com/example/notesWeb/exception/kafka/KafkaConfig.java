package com.example.notesWeb.exception.kafka;

import com.example.notesWeb.dtos.NoteDto.NoteUpdateEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.protocol.types.Field;
import org.springframework.beans.factory.annotation.Value;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.checkerframework.checker.units.qual.C;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ProducerListener;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.ExponentialBackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
@lombok.RequiredArgsConstructor
public class KafkaConfig {
    private final com.example.notesWeb.messaging.FailureLedger failures;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapSevers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> kafkaConfig = new HashMap<>();
        kafkaConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapSevers);
        kafkaConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        //Zstd compression: Reduces 0.6MB to approximately 150KB (Saves 75% bandwidth)
        kafkaConfig.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "zstd");
        kafkaConfig.put(ProducerConfig.ACKS_CONFIG, "all");
        kafkaConfig.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);
        kafkaConfig.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

        //Linger & Batch: Wait 20ms to consolidate updates sent by multiple users at once
        kafkaConfig.put(ProducerConfig.LINGER_MS_CONFIG, 20);
        kafkaConfig.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536); //64KB

        return new DefaultKafkaProducerFactory<>(kafkaConfig);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, Object> consumerFactory () {
        Map<String, Object> objectMap = new HashMap<>();
        objectMap.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapSevers);
        objectMap.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        //prevent overload message
        objectMap.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

        //no lost message when consumer start
        objectMap.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        objectMap.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        objectMap.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        objectMap.put(ConsumerConfig.GROUP_ID_CONFIG, "note-update-group");
        objectMap.put(JsonDeserializer.TRUSTED_PACKAGES, "com.example.notesWeb");//not use "*" when production scale a lot of instance

        //prevent bug deserialize when running lot of service
        objectMap.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NoteUpdateEvent.class);
        objectMap.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(objectMap);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
            ConsumerFactory<String, Object> consumerFactory) {

        ConcurrentKafkaListenerContainerFactory<String, Object> kafkaFactory = new ConcurrentKafkaListenerContainerFactory<>();
        kafkaFactory.setConsumerFactory(consumerFactory);

        //Scale all 8 threads in one instance
        kafkaFactory.setConcurrency(8);
        kafkaFactory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        //Exponential Backoff: Try again after 2s->4s->8s (Avoid crashing the database)
        DefaultErrorHandler errorHandler = new DefaultErrorHandler((record, exception) -> {
            String key = "kafka:"+record.topic()+":"+record.partition()+":"+record.offset();
            try {
                failures.dead(key, "kafka:"+record.topic(), json.writeValueAsString(record.value()), "Note update retries exhausted");
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new IllegalStateException("Cannot retain failed message", failure);
            }
        }, new FixedBackOff(1000L, 4L));
        errorHandler.setCommitRecovered(true); // only after FailureLedger transaction commits
        kafkaFactory.setCommonErrorHandler(errorHandler);
        return kafkaFactory;
    }
}
