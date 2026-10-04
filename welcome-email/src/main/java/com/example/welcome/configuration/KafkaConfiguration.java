package com.example.welcome.configuration;

import com.example.welcome.application.DeliveryInProgress;
import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean NewTopic registrationTopic(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(1).build();
    }

    @Bean NewTopic deadLetterTopic(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(3).replicas(1).build();
    }

    @Bean DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafka,
            @Value("${app.kafka.retry.initial-ms:2000}") long initialMs,
            @Value("${app.kafka.retry.max-ms:16000}") long maxMs) {
        var recoverer = new DeadLetterPublishingRecoverer(kafka,
                (record, failure) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
        var backoff = new ExponentialBackOffWithMaxRetries(4);
        backoff.setInitialInterval(initialMs);
        backoff.setMultiplier(2);
        backoff.setMaxInterval(maxMs);
        var handler = new DefaultErrorHandler(recoverer, backoff);
        handler.setBackOffFunction((record, failure) -> {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof DeliveryInProgress) return new FixedBackOff(5000, 12);
            }
            return null; // Use the exponential policy for delivery failures.
        });
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
