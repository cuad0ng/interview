package com.example.welcome.application;

import com.example.welcome.application.port.*;
import com.example.welcome.domain.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UseCasesTest {
    private final UserRegistered event = new UserRegistered(UUID.randomUUID(), UUID.randomUUID(),
            "thanh@example.test", "Thanh", Instant.parse("2026-10-04T00:00:00Z"));

    @Test
    void registrationNormalizesAndAtomicallyStoresUserAndEvent() {
        RegistrationStore store = mock(RegistrationStore.class);
        var useCase = new RegisterUser(store, Clock.fixed(event.occurredAt(), ZoneOffset.UTC));
        User user = useCase.execute(" Thanh@Example.test ", " Thanh ");
        var captured = ArgumentCaptor.forClass(UserRegistered.class);
        verify(store).save(eq(user), captured.capture());
        assertThat(user.email()).isEqualTo("thanh@example.test");
        assertThat(user.name()).isEqualTo("Thanh");
        assertThat(captured.getValue().userId()).isEqualTo(user.id());
        assertThat(captured.getValue().occurredAt()).isEqualTo(event.occurredAt());
    }

    @Test
    void publisherFailureLeavesEventAvailableForRetry() {
        Outbox outbox = mock(Outbox.class);
        EventPublisher publisher = mock(EventPublisher.class);
        var claimed = new Outbox.ClaimedEvent(event, UUID.randomUUID());
        when(outbox.claim(1)).thenReturn(List.of(claimed), List.of());
        doThrow(new IllegalStateException("Broker unavailable")).when(publisher).publish(event);
        new PublishPendingEvents(outbox, publisher).execute();
        verify(outbox).retryLater(claimed);
        verify(outbox, never()).published(any());
    }

    @Test
    void publishedIsMarkedOnlyAfterBrokerAck() {
        Outbox outbox = mock(Outbox.class);
        EventPublisher publisher = mock(EventPublisher.class);
        var claimed = new Outbox.ClaimedEvent(event, UUID.randomUUID());
        when(outbox.claim(1)).thenReturn(List.of(claimed), List.of());
        new PublishPendingEvents(outbox, publisher).execute();
        var order = inOrder(publisher, outbox);
        order.verify(publisher).publish(event);
        order.verify(outbox).published(claimed);
    }

    @Test
    void sentEventDoesNotSendAgain() {
        DeliveryStore store = mock(DeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        when(store.claim(event.eventId())).thenReturn(
                new DeliveryStore.Claim(event.eventId(), UUID.randomUUID(), DeliveryStore.State.SENT));
        new SendWelcomeEmail(store, sender).execute(event);
        verifyNoInteractions(sender);
    }

    @Test
    void successfulEmailIsMarkedSentAfterSmtpReturns() {
        DeliveryStore store = mock(DeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        var claim = new DeliveryStore.Claim(event.eventId(), UUID.randomUUID(), DeliveryStore.State.CLAIMED);
        when(store.claim(event.eventId())).thenReturn(claim);
        new SendWelcomeEmail(store, sender).execute(event);
        var order = inOrder(sender, store);
        order.verify(sender).sendWelcome(event);
        order.verify(store).sent(claim);
    }

    @Test
    void busyLeaseDoesNotSendAndSignalsRetry() {
        DeliveryStore store = mock(DeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        when(store.claim(event.eventId())).thenReturn(
                new DeliveryStore.Claim(event.eventId(), UUID.randomUUID(), DeliveryStore.State.BUSY));
        assertThatThrownBy(() -> new SendWelcomeEmail(store, sender).execute(event))
                .isInstanceOf(DeliveryInProgress.class);
        verifyNoInteractions(sender);
    }

    @Test
    void smtpFailureReleasesClaimAndPropagatesForKafkaRetry() {
        DeliveryStore store = mock(DeliveryStore.class);
        EmailSender sender = mock(EmailSender.class);
        var claim = new DeliveryStore.Claim(event.eventId(), UUID.randomUUID(), DeliveryStore.State.CLAIMED);
        when(store.claim(event.eventId())).thenReturn(claim);
        doThrow(new IllegalStateException("SMTP unavailable")).when(sender).sendWelcome(event);
        assertThatThrownBy(() -> new SendWelcomeEmail(store, sender).execute(event))
                .isInstanceOf(IllegalStateException.class);
        verify(store).release(claim);
        verify(store, never()).sent(any());
    }
}
