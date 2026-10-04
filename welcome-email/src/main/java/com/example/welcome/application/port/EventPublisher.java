package com.example.welcome.application.port;

import com.example.welcome.domain.UserRegistered;

public interface EventPublisher {
    /** Return only after the broker acknowledges the event; throw on unknown/failure. */
    void publish(UserRegistered event);
}
