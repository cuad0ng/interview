package com.example.welcome.application.port;

import com.example.welcome.domain.UserRegistered;
import java.util.List;
import java.util.UUID;

public interface Outbox {
    record ClaimedEvent(UserRegistered event, UUID token) {}
    List<ClaimedEvent> claim(int limit);
    void published(ClaimedEvent event);
    void retryLater(ClaimedEvent event);
}
