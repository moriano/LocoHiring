package com.locohiring.service.dao;

import com.locohiring.api.model.Event;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/** The audit log. Services record an event for every change they make. */
@Repository
public class EventDao {

    private static final RowMapper<Event> EVENT = (rs, n) -> new Event()
            .id(rs.getLong("id"))
            .funnelId(Rows.getLong(rs, "funnel_id"))
            .applicationId(Rows.getLong(rs, "application_id"))
            .email(rs.getString("email"))
            .timestamp(Rows.getTime(rs, "timestamp"))
            .message(rs.getString("message"));

    private final JdbcClient jdbc;

    public EventDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void record(String email, Long funnelId, Long applicationId, String message) {
        jdbc.sql("""
                        INSERT INTO events (email, funnel_id, application_id, message)
                        VALUES (:email, :funnelId, :applicationId, :message)""")
                .param("email", email).param("funnelId", funnelId).param("applicationId", applicationId)
                .param("message", message)
                .update();
    }

    public List<Event> findByApplication(long applicationId) {
        return jdbc.sql("SELECT * FROM events WHERE application_id = :id ORDER BY timestamp DESC, id DESC")
                .param("id", applicationId).query(EVENT).list();
    }

    public List<Event> findLatest(int limit) {
        return jdbc.sql("SELECT * FROM events ORDER BY timestamp DESC, id DESC LIMIT :limit")
                .param("limit", limit).query(EVENT).list();
    }
}
