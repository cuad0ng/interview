package com.example.welcome.application;

public class DeliveryInProgress extends RuntimeException {
    public DeliveryInProgress() {
        super("Email delivery already in progress");
    }
}
