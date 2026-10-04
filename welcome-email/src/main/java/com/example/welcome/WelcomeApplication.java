package com.example.welcome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WelcomeApplication {
    public static void main(String[] args) {
        SpringApplication.run(WelcomeApplication.class, args);
    }
}
