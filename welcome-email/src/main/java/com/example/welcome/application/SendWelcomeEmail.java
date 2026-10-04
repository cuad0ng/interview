package com.example.welcome.application;

import com.example.welcome.application.port.DeliveryStore;
import com.example.welcome.application.port.EmailSender;
import com.example.welcome.domain.UserRegistered;

public class SendWelcomeEmail {
    private final DeliveryStore deliveries;
    private final EmailSender sender;

    public SendWelcomeEmail(DeliveryStore deliveries, EmailSender sender) {
        this.deliveries = deliveries;
        this.sender = sender;
    }

    public void execute(UserRegistered event) {
        var claim = deliveries.claim(event.eventId());
        if (claim.state() == DeliveryStore.State.SENT) return;
        if (claim.state() == DeliveryStore.State.BUSY) {
            throw new DeliveryInProgress();
        }
        try {
            sender.sendWelcome(event);
        } catch (RuntimeException failure) {
            deliveries.release(claim);
            throw failure;
        }
        // SMTP and DB cannot commit atomically: a crash before this update can duplicate email.
        deliveries.sent(claim);
    }
}
