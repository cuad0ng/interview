package com.example.welcome.adapter.out.email;

import com.example.welcome.application.port.EmailSender;
import com.example.welcome.domain.UserRegistered;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailSender implements EmailSender {
    private final JavaMailSender mail;
    private final String from;

    public SmtpEmailSender(JavaMailSender mail, @Value("${app.mail.from}") String from) {
        this.mail = mail;
        this.from = from;
    }

    @Override
    public void sendWelcome(UserRegistered event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.email());
        message.setSubject("Welcome!");
        message.setText("Hi " + event.name() + ",\n\nWelcome! Your registration is complete.");
        mail.send(message);
    }
}
