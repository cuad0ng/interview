package com.example.welcome.application;

public class EmailAlreadyRegistered extends RuntimeException {
    public EmailAlreadyRegistered() {
        super("Email already registered");
    }
}
