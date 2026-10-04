package com.example.welcome.application.port;

import com.example.welcome.domain.UserRegistered;

public interface EmailSender {
    void sendWelcome(UserRegistered event);
}
