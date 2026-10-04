package com.example.welcome.application.port;

import java.util.UUID;

public interface DeliveryStore {
    enum State { CLAIMED, SENT, BUSY }
    record Claim(UUID eventId, UUID token, State state) {}
    Claim claim(UUID eventId);
    void sent(Claim claim);
    void release(Claim claim);
}
