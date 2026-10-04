package com.example.welcome.domain;

import java.util.UUID;

public record User(UUID id, String email, String name) {}
