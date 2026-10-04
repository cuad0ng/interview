package com.example.welcome.application;

import com.example.welcome.application.port.RegistrationStore;
import com.example.welcome.domain.User;
import com.example.welcome.domain.UserRegistered;
import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

public class RegisterUser {
    private final RegistrationStore store;
    private final Clock clock;

    public RegisterUser(RegistrationStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public User execute(String email, String name) {
        User user = new User(UUID.randomUUID(), email.strip().toLowerCase(Locale.ROOT), name.strip());
        var event = new UserRegistered(UUID.randomUUID(), user.id(), user.email(), user.name(), clock.instant());
        store.save(user, event);
        return user;
    }
}
