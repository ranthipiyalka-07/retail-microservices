package com.retail.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

public class JdbcOutboxStore implements OutboxStore {

    private final JdbcTemplate jdbc;

    public JdbcOutboxStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(String eventId, String topic, String key, String eventType, String payload, String traceParent) {
        jdbc.update("INSERT INTO outbox_events (event_id, topic, event_key, event_type, payload, trace_parent, attempts, "
                        + "next_attempt_at, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                eventId, topic, key, eventType, payload, traceParent);
    }

    @Override
    public List<OutboxRow> findPending(int limit) {
        return jdbc.query("SELECT id, event_id, topic, event_key, event_type, payload, trace_parent, attempts, "
                        + "(next_attempt_at <= UTC_TIMESTAMP(6) AND (locked_until IS NULL OR locked_until < UTC_TIMESTAMP(6))) AS due "
                        + "FROM outbox_events WHERE sent_at IS NULL ORDER BY id LIMIT ?",
                (rs, rowNum) -> new OutboxRow(rs.getLong("id"), rs.getString("event_id"), rs.getString("topic"),
                        rs.getString("event_key"), rs.getString("event_type"), rs.getString("payload"),
                        rs.getString("trace_parent"), rs.getInt("attempts"), rs.getBoolean("due")),
                limit);
    }

    @Override
    public boolean claim(long id, int leaseSeconds) {
        return jdbc.update("UPDATE outbox_events SET locked_until = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND), "
                + "attempts = attempts + 1 WHERE id = ? AND sent_at IS NULL AND next_attempt_at <= UTC_TIMESTAMP(6) "
                + "AND (locked_until IS NULL OR locked_until < UTC_TIMESTAMP(6))", leaseSeconds, id) == 1;
    }

    @Override
    public void markSent(long id) {
        jdbc.update("UPDATE outbox_events SET sent_at = UTC_TIMESTAMP(6), locked_until = NULL, last_error = NULL WHERE id = ?", id);
    }

    @Override
    public void markFailed(long id, int retryInSeconds, String error) {
        jdbc.update("UPDATE outbox_events SET next_attempt_at = DATE_ADD(UTC_TIMESTAMP(6), INTERVAL ? SECOND), "
                + "locked_until = NULL, last_error = ? WHERE id = ? AND sent_at IS NULL", retryInSeconds, error, id);
    }

    @Override
    public int deleteSentOlderThan(int days) {
        return jdbc.update("DELETE FROM outbox_events WHERE sent_at IS NOT NULL "
                + "AND sent_at < DATE_SUB(UTC_TIMESTAMP(6), INTERVAL ? DAY)", days);
    }
}