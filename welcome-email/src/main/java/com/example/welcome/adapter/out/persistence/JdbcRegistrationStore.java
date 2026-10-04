package com.example.welcome.adapter.out.persistence;

import com.example.welcome.application.EmailAlreadyRegistered;
import com.example.welcome.application.port.RegistrationStore;
import com.example.welcome.domain.User;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcRegistrationStore implements RegistrationStore {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public JdbcRegistrationStore(JdbcTemplate db, TransactionTemplate transactions, ObjectMapper json) {
        this.db = db;
        this.transactions = transactions;
        this.json = json;
    }

    @Override
    public void save(User user, UserRegistered event) {
        String payload;
        try {
            payload = json.writeValueAsString(event);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize registration event", failure);
        }
        transactions.executeWithoutResult(tx -> {
            try {
                db.update("INSERT INTO app_user(id, email, display_name) VALUES (?, ?, ?)",
                        user.id(), user.email(), user.name());
            } catch (DuplicateKeyException duplicate) {
                throw new EmailAlreadyRegistered();
            }
            db.update("INSERT INTO outbox_event(event_id, user_id, payload) VALUES (?, ?, ?)",
                    event.eventId(), event.userId(), payload);
        });
    }
}
