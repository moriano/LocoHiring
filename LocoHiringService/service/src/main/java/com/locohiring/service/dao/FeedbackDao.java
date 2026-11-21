package com.locohiring.service.dao;

import com.locohiring.api.model.Feedback;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FeedbackDao {

    private static final String SQL = """
            SELECT f.id, f.interview_id, f.interviewer_id, f.feedback, f.inclined, f.created_at,
                   it.name AS interviewtype_name, concat_ws(' ', iv.first_name, iv.surname) AS interviewer_name
            FROM interview_feedback f
            JOIN interviews i ON i.id = f.interview_id
            JOIN interview_types it ON it.id = i.interviewtype_id
            JOIN interviewers iv ON iv.id = f.interviewer_id
            """;

    private static final RowMapper<Feedback> FEEDBACK = (rs, n) -> new Feedback()
            .id(rs.getLong("id"))
            .interviewId(rs.getLong("interview_id"))
            .interviewTypeName(rs.getString("interviewtype_name"))
            .interviewerId(rs.getLong("interviewer_id"))
            .interviewerName(rs.getString("interviewer_name"))
            .feedback(rs.getString("feedback"))
            .inclined(rs.getBoolean("inclined"))
            .createdAt(Rows.getTime(rs, "created_at"));

    private final JdbcClient jdbc;

    public FeedbackDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Feedback> findByApplicationIds(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SQL + " WHERE i.application_id IN (:ids) ORDER BY i.scheduled_at, f.created_at")
                .param("ids", applicationIds).query(FEEDBACK).list();
    }

    public boolean exists(long interviewId, long interviewerId) {
        return jdbc.sql("""
                        SELECT count(*) FROM interview_feedback
                        WHERE interview_id = :interviewId AND interviewer_id = :interviewerId""")
                .param("interviewId", interviewId).param("interviewerId", interviewerId)
                .query(Long.class).single() > 0;
    }

    public Feedback create(long interviewId, long interviewerId, String feedback, boolean inclined) {
        long id = jdbc.sql("""
                        INSERT INTO interview_feedback (interview_id, interviewer_id, feedback, inclined)
                        VALUES (:interviewId, :interviewerId, :feedback, :inclined) RETURNING id""")
                .param("interviewId", interviewId).param("interviewerId", interviewerId)
                .param("feedback", feedback).param("inclined", inclined)
                .query(Long.class).single();
        return jdbc.sql(SQL + " WHERE f.id = :id").param("id", id).query(FEEDBACK).single();
    }
}
