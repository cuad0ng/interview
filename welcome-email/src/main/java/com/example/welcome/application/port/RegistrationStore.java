package com.example.welcome.application.port;

import com.example.welcome.domain.User;
import com.example.welcome.domain.UserRegistered;

public interface RegistrationStore {
    /** Atomically save both records; duplicate email throws EmailAlreadyRegistered. */
    void save(User user, UserRegistered event);
}
