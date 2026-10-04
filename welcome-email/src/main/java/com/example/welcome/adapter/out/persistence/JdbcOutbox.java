package com.example.welcome.adapter.out.persistence;

import com.example.welcome.application.port.Outbox;
import com.example.welcome.domain.UserRegistered;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcOutbox implements Outbox {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;

    public JdbcOutbox(JdbcTemplate db, TransactionTemplate transactions, ObjectMapper json) {
        this.db = db;
        this.transactions = transactions;
        this.json = json;
    }

    @Override
    public List<ClaimedEvent> claim(int limit) {
        return Objects.requireNonNull(transactions.execute(tx -> {
            var events = db.query("""
                    SELECT payload FROM outbox_event
                    WHERE status <> 'PUBLISHED' AND available_at <= CURRENT_TIMESTAMP
                    ORDER BY created_at, event_id LIMIT ? FOR UPDATE SKIP LOCKED
                    """, (rs, row) -> decode(rs.getString("payload")), limit);
            return events.stream().map(event -> {
                UUID token = UUID.randomUUID();
                db.update("""
                        UPDATE outbox_event SET status = 'PROCESSING', claim_token = ?,
                        available_at = CURRENT_TIMESTAMP + INTERVAL '60 seconds', attempts = attempts + 1
                        WHERE event_id = ?
                        """, token, event.eventId());
                return new ClaimedEvent(event, token);
            }).toList();
        }));
    }

    @Override
    public void published(ClaimedEvent event) {
        int updated = db.update("""
                UPDATE outbox_event SET status = 'PUBLISHED', published_at = CURRENT_TIMESTAMP,
                claim_token = NULL WHERE event_id = ? AND claim_token = ? AND status = 'PROCESSING'
                """, event.event().eventId(), event.token());
        if (updated != 1) throw new IllegalStateException("Outbox lease was lost");
    }

    @Override
    public void retryLater(ClaimedEvent event) {
        db.update("""
                UPDATE outbox_event SET status = 'PENDING', claim_token = NULL,
                available_at = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
                WHERE event_id = ? AND claim_token = ? AND status = 'PROCESSING'
                """, event.event().eventId(), event.token());
    }

    private UserRegistered decode(String payload) {
        try {
            return json.readValue(payload, UserRegistered.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Invalid persisted event", failure);
        }
    }
}
