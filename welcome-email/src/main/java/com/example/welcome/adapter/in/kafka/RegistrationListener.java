package com.example.welcome.adapter.in.kafka;

import com.example.welcome.application.SendWelcomeEmail;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RegistrationListener {
    private static final Logger log = LoggerFactory.getLogger(RegistrationListener.class);
    private final SendWelcomeEmail send;
    private final ObjectMapper json;
    private final Validator validator;

    public RegistrationListener(SendWelcomeEmail send, ObjectMapper json, Validator validator) {
        this.send = send;
        this.json = json;
        this.validator = validator;
    }

    public record Message(@NotNull UUID eventId, @NotNull UUID userId,
                          @NotBlank @Email @Size(max = 254) String email,
                          @NotBlank @Size(max = 100) String name, @NotNull Instant occurredAt) {}

    @KafkaListener(topics = "${app.kafka.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        Message message;
        try {
            message = json.readValue(payload, Message.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Invalid registration event JSON");
        }
        if (message == null || !validator.validate(message).isEmpty()) {
            throw new IllegalArgumentException("Invalid registration event fields");
        }
        send.execute(new UserRegistered(message.eventId(), message.userId(), message.email(),
                message.name(), message.occurredAt()));
        log.info("Welcome email event={} processed", message.eventId());
    }
}
