package com.example.welcome.integration;

import com.example.welcome.adapter.out.email.SmtpEmailSender;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
class SmtpEmailSenderIT {
    @Container
    static final GenericContainer<?> mailpit = new GenericContainer<>("axllent/mailpit:v1.27.8")
            .withExposedPorts(1025, 8025)
            .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

    @Test
    void realSmtpDeliversTheWelcomeMessageToMailpit() throws Exception {
        var mail = new JavaMailSenderImpl();
        mail.setHost(mailpit.getHost());
        mail.setPort(mailpit.getMappedPort(1025));
        mail.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
        mail.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
        mail.getJavaMailProperties().setProperty("mail.smtp.writetimeout", "3000");
        var event = new UserRegistered(UUID.randomUUID(), UUID.randomUUID(),
                "thanh@example.test", "Thanh", Instant.now());
        new SmtpEmailSender(mail, "hello@example.test").sendWelcome(event);

        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var json = new ObjectMapper();
        String baseUrl = "http://" + mailpit.getHost() + ":" + mailpit.getMappedPort(8025);
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var response = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/messages"))
                    .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
            var messages = json.readTree(response.body()).path("messages");
            assertThat(messages.size()).isEqualTo(1);
            var message = messages.get(0);
            assertThat(message.path("From").path("Address").asText()).isEqualTo("hello@example.test");
            assertThat(message.path("To").get(0).path("Address").asText()).isEqualTo(event.email());
            assertThat(message.path("Subject").asText()).isEqualTo("Welcome!");
            var detail = client.send(HttpRequest.newBuilder(URI.create(baseUrl + "/api/v1/message/"
                            + message.path("ID").asText())).timeout(Duration.ofSeconds(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(detail.statusCode()).isEqualTo(200);
            assertThat(json.readTree(detail.body()).path("Text").asText())
                    .contains("Hi Thanh", "Your registration is complete.");
        });
    }
}
