package com.example.welcome.adapter.out.persistence;

import com.example.welcome.application.port.DeliveryStore;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class JdbcDeliveryStore implements DeliveryStore {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;

    public JdbcDeliveryStore(JdbcTemplate db, TransactionTemplate transactions) {
        this.db = db;
        this.transactions = transactions;
    }

    @Override
    public Claim claim(UUID eventId) {
        return Objects.requireNonNull(transactions.execute(tx -> {
            UUID token = UUID.randomUUID();
            db.update("""
                    INSERT INTO email_delivery(event_id, status) VALUES (?, 'PENDING')
                    ON CONFLICT (event_id) DO NOTHING
                    """, eventId);
            String status = db.queryForObject(
                    "SELECT status FROM email_delivery WHERE event_id = ? FOR UPDATE", String.class, eventId);
            if ("SENT".equals(status)) return new Claim(eventId, token, State.SENT);
            int updated = db.update("""
                    UPDATE email_delivery SET status = 'PROCESSING', claim_token = ?,
                    lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds'
                    WHERE event_id = ? AND (status = 'PENDING' OR lease_until <= CURRENT_TIMESTAMP)
                    """, token, eventId);
            return new Claim(eventId, token, updated == 1 ? State.CLAIMED : State.BUSY);
        }));
    }

    @Override
    public void sent(Claim claim) {
        int updated = db.update("""
                UPDATE email_delivery SET status = 'SENT', sent_at = CURRENT_TIMESTAMP,
                claim_token = NULL, lease_until = NULL
                WHERE event_id = ? AND claim_token = ? AND status = 'PROCESSING'
                """, claim.eventId(), claim.token());
        if (updated != 1) throw new IllegalStateException("Email delivery lease was lost");
    }

    @Override
    public void release(Claim claim) {
        db.update("""
                UPDATE email_delivery SET status = 'PENDING', claim_token = NULL, lease_until = NULL
                WHERE event_id = ? AND claim_token = ? AND status = 'PROCESSING'
                """, claim.eventId(), claim.token());
    }
}
