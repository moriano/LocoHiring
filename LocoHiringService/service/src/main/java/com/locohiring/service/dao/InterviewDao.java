package com.locohiring.service.dao;

import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewStatus;
import com.locohiring.service.security.AuthUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class InterviewDao {

    private static final String SQL = """
            SELECT i.id, i.application_id, i.interviewer_id, i.shadow_id, i.interviewtype_id, i.status, i.scheduled_at,
                   a.candidate_id, concat_ws(' ', c.first_name, c.surname) AS candidate_name, p.name AS position_name,
                   it.name AS interviewtype_name,
                   concat_ws(' ', iv.first_name, iv.surname) AS interviewer_name,
                   concat_ws(' ', sh.first_name, sh.surname) AS shadow_name
            FROM interviews i
            JOIN applications a ON a.id = i.application_id
            JOIN candidates c ON c.id = a.candidate_id
            JOIN positions p ON p.id = c.position_id
            JOIN interview_types it ON it.id = i.interviewtype_id
            LEFT JOIN interviewers iv ON iv.id = i.interviewer_id
            LEFT JOIN interviewers sh ON sh.id = i.shadow_id
            """;

    private static final RowMapper<Interview> INTERVIEW = (rs, n) -> {
        Long interviewerId = Rows.getLong(rs, "interviewer_id");
        Long shadowId = Rows.getLong(rs, "shadow_id");
        return new Interview()
                .id(rs.getLong("id"))
                .applicationId(rs.getLong("application_id"))
                .candidateId(rs.getLong("candidate_id"))
                .candidateName(rs.getString("candidate_name"))
                .positionName(rs.getString("position_name"))
                .interviewTypeId(rs.getLong("interviewtype_id"))
                .interviewTypeName(rs.getString("interviewtype_name"))
                .interviewerId(interviewerId)
                .interviewerName(interviewerId == null ? null : rs.getString("interviewer_name"))
                .shadowId(shadowId)
                .shadowName(shadowId == null ? null : rs.getString("shadow_name"))
                .status(InterviewStatus.fromValue(rs.getString("status")))
                .scheduledAt(Rows.getTime(rs, "scheduled_at"));
    };

    private final JdbcClient jdbc;

    public InterviewDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Interview> findById(long id) {
        return jdbc.sql(SQL + " WHERE i.id = :id").param("id", id).query(INTERVIEW).optional();
    }

    public List<Interview> findByApplicationIds(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SQL + " WHERE i.application_id IN (:ids) ORDER BY i.scheduled_at")
                .param("ids", applicationIds).query(INTERVIEW).list();
    }

    /**
     * Scheduled interviews that have not ended: the caller's own as an interviewer, those of their applications as a
     * recruiter, all of them as an admin.
     */
    public List<Interview> findUpcoming(AuthUser user, OffsetDateTime now) {
        String filter = user.admin() ? "TRUE"
                : user.isRecruiter() ? "a.recruiter_id = :uid"
                : "(i.interviewer_id = :uid OR i.shadow_id = :uid)";
        return jdbc.sql(SQL + " WHERE " + filter + """
                         AND i.status = 'SCHEDULED' AND i.scheduled_at + interval '1 hour' > :now
                        ORDER BY i.scheduled_at""")
                .param("uid", user.id()).param("now", now).query(INTERVIEW).list();
    }

    /** Interviews of the interviewer that have ended and have no feedback from them yet. */
    public List<Interview> findEndedWithoutFeedback(long interviewerId, OffsetDateTime now) {
        return jdbc.sql(SQL + """
                         WHERE (i.interviewer_id = :uid OR i.shadow_id = :uid)
                           AND i.status IN ('SCHEDULED', 'COMPLETED')
                           AND i.scheduled_at + interval '1 hour' <= :now
                           AND NOT EXISTS (SELECT 1 FROM interview_feedback f
                                           WHERE f.interview_id = i.id AND f.interviewer_id = :uid)
                        ORDER BY i.scheduled_at""")
                .param("uid", interviewerId).param("now", now).query(INTERVIEW).list();
    }

    public long create(long applicationId, long interviewTypeId, OffsetDateTime scheduledAt) {
        return jdbc.sql("""
                        INSERT INTO interviews (application_id, interviewtype_id, scheduled_at)
                        VALUES (:applicationId, :typeId, :scheduledAt) RETURNING id""")
                .param("applicationId", applicationId).param("typeId", interviewTypeId)
                .param("scheduledAt", scheduledAt)
                .query(Long.class).single();
    }

    public void update(long id, OffsetDateTime scheduledAt, Long interviewerId, Long shadowId, InterviewStatus status) {
        jdbc.sql("""
                        UPDATE interviews SET scheduled_at = :scheduledAt, interviewer_id = :interviewerId,
                               shadow_id = :shadowId, status = :status
                        WHERE id = :id""")
                .param("scheduledAt", scheduledAt).param("interviewerId", interviewerId)
                .param("shadowId", shadowId).param("status", status.getValue()).param("id", id)
                .update();
    }

    public void updateStatus(long id, InterviewStatus status) {
        jdbc.sql("UPDATE interviews SET status = :status WHERE id = :id")
                .param("status", status.getValue()).param("id", id).update();
    }
}
