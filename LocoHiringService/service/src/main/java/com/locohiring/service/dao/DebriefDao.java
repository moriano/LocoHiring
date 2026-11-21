package com.locohiring.service.dao;

import com.locohiring.api.model.Debrief;
import com.locohiring.service.security.AuthUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Debriefs. The dueAt and late fields are left for the services to fill in. */
@Repository
public class DebriefDao {

    private static final String SQL = """
            SELECT d.id, d.application_id, d.notes, d.inclined, d.scheduled_at,
                   concat_ws(' ', c.first_name, c.surname) AS candidate_name, f.name AS funnel_name
            FROM debriefs d
            JOIN applications a ON a.id = d.application_id
            JOIN candidates c ON c.id = a.candidate_id
            JOIN funnels f ON f.id = a.funnel_id
            """;

    private static final RowMapper<Debrief> DEBRIEF = (rs, n) -> {
        Boolean inclined = Rows.getBoolean(rs, "inclined");
        String notes = rs.getString("notes");
        return new Debrief()
                .id(rs.getLong("id"))
                .applicationId(rs.getLong("application_id"))
                .candidateName(rs.getString("candidate_name"))
                .funnelName(rs.getString("funnel_name"))
                .scheduledAt(Rows.getTime(rs, "scheduled_at"))
                .notes(notes)
                .inclined(inclined)
                .completed(notes != null && inclined != null)
                .late(false);
    };

    private final JdbcClient jdbc;

    public DebriefDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Debrief> findByApplication(long applicationId) {
        return jdbc.sql(SQL + " WHERE d.application_id = :id").param("id", applicationId).query(DEBRIEF).optional();
    }

    public List<Debrief> findByApplicationIds(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SQL + " WHERE d.application_id IN (:ids)").param("ids", applicationIds)
                .query(DEBRIEF).list();
    }

    /**
     * Debriefs after now that the user attends: interviewers of the application, its recruiter; admins see all.
     */
    public List<Debrief> findUpcoming(AuthUser user, OffsetDateTime now) {
        String filter = user.admin() ? "TRUE"
                : user.isRecruiter() ? "a.recruiter_id = :uid"
                : """
                  EXISTS (SELECT 1 FROM interviews i WHERE i.application_id = a.id AND i.status <> 'CANCELLED'
                          AND (i.interviewer_id = :uid OR i.shadow_id = :uid))""";
        return jdbc.sql(SQL + " WHERE d.scheduled_at > :now AND " + filter + " ORDER BY d.scheduled_at")
                .param("uid", user.id()).param("now", now).query(DEBRIEF).list();
    }

    public void create(long applicationId, OffsetDateTime scheduledAt) {
        jdbc.sql("INSERT INTO debriefs (application_id, scheduled_at) VALUES (:id, :scheduledAt)")
                .param("id", applicationId).param("scheduledAt", scheduledAt).update();
    }

    public void update(long applicationId, OffsetDateTime scheduledAt, String notes, Boolean inclined) {
        jdbc.sql("""
                        UPDATE debriefs SET scheduled_at = :scheduledAt, notes = :notes, inclined = :inclined
                        WHERE application_id = :id""")
                .param("scheduledAt", scheduledAt).param("notes", notes).param("inclined", inclined)
                .param("id", applicationId)
                .update();
    }
}
