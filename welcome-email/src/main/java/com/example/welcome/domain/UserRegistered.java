package com.example.welcome.domain;

import java.time.Instant;
import java.util.UUID;

public record UserRegistered(UUID eventId, UUID userId, String email, String name, Instant occurredAt) {}
