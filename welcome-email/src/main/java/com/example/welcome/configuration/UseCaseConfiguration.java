package com.example.welcome.configuration;

import com.example.welcome.application.*;
import com.example.welcome.application.port.*;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCaseConfiguration {
    @Bean Clock clock() { return Clock.systemUTC(); }
    @Bean RegisterUser registerUser(RegistrationStore store, Clock clock) {
        return new RegisterUser(store, clock);
    }
    @Bean PublishPendingEvents publishPendingEvents(Outbox outbox, EventPublisher publisher) {
        return new PublishPendingEvents(outbox, publisher);
    }
    @Bean SendWelcomeEmail sendWelcomeEmail(DeliveryStore store, EmailSender sender) {
        return new SendWelcomeEmail(store, sender);
    }
}
