package com.example.welcome.adapter;

import com.example.welcome.adapter.in.kafka.RegistrationListener;
import com.example.welcome.adapter.out.email.SmtpEmailSender;
import com.example.welcome.adapter.out.persistence.JdbcRegistrationStore;
import com.example.welcome.application.SendWelcomeEmail;
import com.example.welcome.domain.User;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RegistrationAdaptersTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final UserRegistered event = new UserRegistered(UUID.randomUUID(), UUID.randomUUID(),
            "thanh@example.test", "Thanh", Instant.parse("2026-10-04T00:00:00Z"));

    @Test
    void outboxForeignKeyMatchesTheUserIdInItsPayload() throws Exception {
        var db = mock(JdbcTemplate.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.<Consumer<TransactionStatus>>getArgument(0).accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        // An inconsistent caller must not silently replace the event's foreign key.
        var user = new User(UUID.randomUUID(), event.email(), event.name());
        new JdbcRegistrationStore(db, transactions, json).save(user, event);
        var foreignKey = ArgumentCaptor.forClass(UUID.class);
        var payload = ArgumentCaptor.forClass(String.class);
        verify(db).update(startsWith("INSERT INTO outbox_event"), eq(event.eventId()),
                foreignKey.capture(), payload.capture());
        assertThat(foreignKey.getValue()).isEqualTo(json.readValue(payload.getValue(), UserRegistered.class).userId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "{}", "{\"email\":\"bad\"}"})
    void malformedEventsNeverReachTheEmailUseCase(String payload) {
        var send = mock(SendWelcomeEmail.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var listener = new RegistrationListener(send, json, factory.getValidator());
            assertThatThrownBy(() -> listener.consume(payload)).isInstanceOf(IllegalArgumentException.class);
            verifyNoInteractions(send);
        }
    }

    @Test
    void validEventReachesTheEmailUseCase() throws Exception {
        var send = mock(SendWelcomeEmail.class);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            new RegistrationListener(send, json, factory.getValidator()).consume(json.writeValueAsString(event));
            verify(send).execute(event);
        }
    }

    @Test
    void welcomeEmailHasTheConfiguredSenderAndRegisteredRecipient() {
        var mail = mock(JavaMailSender.class);
        new SmtpEmailSender(mail, "hello@example.test").sendWelcome(event);
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("hello@example.test");
        assertThat(message.getValue().getTo()).containsExactly(event.email());
        assertThat(message.getValue().getSubject()).isEqualTo("Welcome!");
        assertThat(message.getValue().getText()).contains(event.name(), "registration is complete");
    }
}
