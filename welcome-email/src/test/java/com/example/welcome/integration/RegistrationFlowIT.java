package com.example.welcome.integration;

import com.example.welcome.adapter.in.kafka.RegistrationListener;
import com.example.welcome.application.*;
import com.example.welcome.application.port.*;
import com.example.welcome.domain.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {"app.outbox.enabled=false", "app.kafka.retry.initial-ms=50", "app.kafka.retry.max-ms=200"})
@AutoConfigureMockMvc
class RegistrationFlowIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6");
    @Container static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.0.2"));

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", postgres::getJdbcUrl);
        properties.add("spring.datasource.username", postgres::getUsername);
        properties.add("spring.datasource.password", postgres::getPassword);
        properties.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired RegisterUser register;
    @Autowired PublishPendingEvents relay;
    @Autowired RegistrationStore registrations;
    @Autowired Outbox outbox;
    @Autowired DeliveryStore deliveries;
    @Autowired RegistrationListener listener;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired MockMvc http;
    @Autowired KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean JavaMailSender mail;

    @BeforeEach
    void clear() {
        db.execute("TRUNCATE email_delivery, outbox_event, app_user");
    }

    @Test
    void httpRegistrationPublishesToKafkaAndConsumerSendsEmail() throws Exception {
        http.perform(post("/users").contentType("application/json")
                .content("{\"email\":\"Thanh@example.test\",\"name\":\"Thanh\"}"))
                .andExpect(status().isCreated());
        verifyNoInteractions(mail);
        String payload = db.queryForObject("SELECT payload FROM outbox_event", String.class);
        relay.execute();
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            assertThat(db.queryForObject("SELECT status FROM email_delivery", String.class)).isEqualTo("SENT");
            verify(mail, times(1)).send(any(SimpleMailMessage.class));
        });
        assertThat(db.queryForObject("SELECT status FROM outbox_event", String.class)).isEqualTo("PUBLISHED");
        listener.consume(payload);
        listener.consume(payload);
        verify(mail, times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void outboxInsertFailureRollsBackUser() {
        User user = new User(UUID.randomUUID(), "rollback@example.test", "Thanh");
        var invalidForeignKey = new UserRegistered(UUID.randomUUID(), UUID.randomUUID(),
                user.email(), user.name(), Instant.now());
        assertThatThrownBy(() -> registrations.save(user, invalidForeignKey))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM app_user", Integer.class)).isZero();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class)).isZero();
    }

    @Test
    void claimsAreExclusiveAndStaleOutboxOwnerCannotMarkPublished() {
        register.execute("claim@example.test", "Thanh");
        var first = outbox.claim(1).get(0);
        assertThat(outbox.claim(1)).isEmpty();
        db.update("UPDATE outbox_event SET available_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var replacement = outbox.claim(1).get(0);
        assertThat(replacement.token()).isNotEqualTo(first.token());
        assertThatThrownBy(() -> outbox.published(first)).isInstanceOf(IllegalStateException.class);
        outbox.published(replacement);

        var emailClaim = deliveries.claim(first.event().eventId());
        assertThat(deliveries.claim(first.event().eventId()).state()).isEqualTo(DeliveryStore.State.BUSY);
        deliveries.sent(emailClaim);
        assertThat(deliveries.claim(first.event().eventId()).state()).isEqualTo(DeliveryStore.State.SENT);
    }

    @Test
    void expiredDeliveryLeaseCanBeReclaimedAndStaleOwnerCannotChangeIt() {
        UUID eventId = UUID.randomUUID();
        var stale = deliveries.claim(eventId);
        db.update("UPDATE email_delivery SET lease_until = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        var current = deliveries.claim(eventId);
        assertThat(current.state()).isEqualTo(DeliveryStore.State.CLAIMED);
        assertThat(current.token()).isNotEqualTo(stale.token());
        deliveries.release(stale);
        assertThat(deliveries.claim(eventId).state()).isEqualTo(DeliveryStore.State.BUSY);
        assertThatThrownBy(() -> deliveries.sent(stale)).isInstanceOf(IllegalStateException.class);
        deliveries.sent(current);
        assertThat(deliveries.claim(eventId).state()).isEqualTo(DeliveryStore.State.SENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}"})
    void invalidEventsGoToDltWithoutSendingEmail(String payload) throws Exception {
        String key = UUID.randomUUID().toString();
        try (var consumer = dltConsumer()) {
            consumer.subscribe(List.of("users.registered.v1.DLT"));
            kafkaTemplate.send("users.registered.v1", key, payload).get(10, java.util.concurrent.TimeUnit.SECONDS);
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (key.equals(record.key())) {
                        assertThat(record.value()).isEqualTo(payload);
                        return true;
                    }
                }
                return false;
            });
        }
        verifyNoInteractions(mail);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM email_delivery", Integer.class)).isZero();
    }

    @Test
    void smtpFailureRetriesFiveDeliveriesThenPublishesOriginalEventToDlt() throws Exception {
        doThrow(new MailSendException("SMTP unavailable")).when(mail).send(any(SimpleMailMessage.class));
        register.execute("dead@example.test", "Thanh");
        UserRegistered event = json.readValue(db.queryForObject("SELECT payload FROM outbox_event", String.class),
                UserRegistered.class);
        try (var consumer = dltConsumer()) {
            consumer.subscribe(List.of("users.registered.v1.DLT"));
            relay.execute();
            await().atMost(Duration.ofSeconds(30)).until(() -> {
                for (var record : consumer.poll(Duration.ofMillis(250))) {
                    if (json.readTree(record.value()).path("eventId").asText().equals(event.eventId().toString())) {
                        return record.key().equals(event.userId().toString());
                    }
                }
                return false;
            });
        }
        verify(mail, times(5)).send(any(SimpleMailMessage.class));
        assertThat(db.queryForObject("SELECT status FROM email_delivery", String.class)).isEqualTo("PENDING");
    }

    @Test
    void duplicateEmailAndInvalidRequestAreRejectedWithoutMoreEvents() throws Exception {
        register.execute("thanh@example.test", "Thanh");
        http.perform(post("/users").contentType("application/json")
                .content("{\"email\":\"THANH@example.test\",\"name\":\"Other\"}"))
                .andExpect(status().isConflict());
        http.perform(post("/users").contentType("application/json")
                .content("{\"email\":\"bad\",\"name\":\"Thanh\"}"))
                .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM outbox_event", Integer.class)).isEqualTo(1);
        verifyNoInteractions(mail);
    }

    private KafkaConsumer<String, String> dltConsumer() {
        Map<String, Object> properties = Map.of(
                "bootstrap.servers", kafka.getBootstrapServers(),
                "group.id", "dlt-test-" + UUID.randomUUID(),
                "auto.offset.reset", "earliest",
                "enable.auto.commit", false,
                "key.deserializer", StringDeserializer.class,
                "value.deserializer", StringDeserializer.class);
        return new KafkaConsumer<>(properties);
    }
}
