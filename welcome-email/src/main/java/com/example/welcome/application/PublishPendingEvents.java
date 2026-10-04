package com.example.welcome.application;

import com.example.welcome.application.port.EventPublisher;
import com.example.welcome.application.port.Outbox;

public class PublishPendingEvents {
    private final Outbox outbox;
    private final EventPublisher publisher;

    public PublishPendingEvents(Outbox outbox, EventPublisher publisher) {
        this.outbox = outbox;
        this.publisher = publisher;
    }

    public void execute() {
        // One claim per send keeps lease time independent of the batch duration.
        for (int i = 0; i < 20; i++) {
            var claimed = outbox.claim(1);
            if (claimed.isEmpty()) return;
            var event = claimed.get(0);
            try {
                publisher.publish(event.event());
            } catch (RuntimeException failure) {
                outbox.retryLater(event);
                continue;
            }
            outbox.published(event);
        }
    }
}
