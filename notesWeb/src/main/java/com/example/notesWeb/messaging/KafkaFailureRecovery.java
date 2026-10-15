package com.example.notesWeb.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.core.log.LogAccessor;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.support.serializer.SerializationUtils;
import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class KafkaFailureRecovery implements ConsumerRecordRecoverer {
    private final FailureLedger failures;
    private final ObjectMapper json;
    @Override
    public void accept(ConsumerRecord<?,?> record, Exception failure) {
        String key="kafka:"+record.topic()+":"+record.partition()+":"+record.offset();
        try {
            var decodeFailure=SerializationUtils.getExceptionFromHeader(record,
                    SerializationUtils.VALUE_DESERIALIZER_EXCEPTION_HEADER,new LogAccessor(KafkaFailureRecovery.class));
            String payload=decodeFailure==null ? json.writeValueAsString(record.value())
                    : json.writeValueAsString(Map.of("rawValueBase64",Base64.getEncoder().encodeToString(decodeFailure.getData())));
            failures.dead(key,"kafka:"+record.topic(),payload,"Note update failed; retained before offset recovery");
        } catch (com.fasterxml.jackson.core.JsonProcessingException cannotRetain) {
            throw new IllegalStateException("Cannot retain Kafka failure",cannotRetain);
        }
    }
}
