package com.example.welcome.adapter.in.scheduling;

import com.example.welcome.application.PublishPendingEvents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final PublishPendingEvents publish;

    public OutboxScheduler(PublishPendingEvents publish) {
        this.publish = publish;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    public void poll() {
        publish.execute();
    }
}
