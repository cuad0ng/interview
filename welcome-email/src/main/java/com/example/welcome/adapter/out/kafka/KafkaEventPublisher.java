package com.example.welcome.adapter.out.kafka;

import com.example.welcome.application.port.EventPublisher;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {
    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    private final String topic;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafka, ObjectMapper json,
                               @Value("${app.kafka.topic}") String topic) {
        this.kafka = kafka;
        this.json = json;
        this.topic = topic;
    }

    @Override
    public void publish(UserRegistered event) {
        try {
            kafka.send(topic, event.userId().toString(), json.writeValueAsString(event)).get(10, TimeUnit.SECONDS);
            log.info("Registration event={} published", event.eventId());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publish interrupted", interrupted);
        } catch (JsonProcessingException | ExecutionException | TimeoutException failure) {
            log.warn("Registration event={} publish failed: {}", event.eventId(), failure.getClass().getSimpleName());
            throw new IllegalStateException("Kafka publish failed", failure);
        }
    }
}
