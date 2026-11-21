package com.locohiring.service.dao;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.ApplicationStatus;
import com.locohiring.service.security.AuthUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class ApplicationDao {

    private static final String SQL = """
            SELECT a.id, a.candidate_id, a.funnel_id, a.recruiter_id, a.status, a.created_at,
                   concat_ws(' ', c.first_name, c.surname) AS candidate_name, f.name AS funnel_name,
                   concat_ws(' ', r.first_name, r.surname) AS recruiter_name
            FROM applications a
            JOIN candidates c ON c.id = a.candidate_id
            JOIN funnels f ON f.id = a.funnel_id
            JOIN recruiters r ON r.id = a.recruiter_id
            """;

    private static final RowMapper<Application> APPLICATION = (rs, n) -> new Application()
            .id(rs.getLong("id"))
            .candidateId(rs.getLong("candidate_id"))
            .candidateName(rs.getString("candidate_name"))
            .funnelId(rs.getLong("funnel_id"))
            .funnelName(rs.getString("funnel_name"))
            .recruiterId(rs.getLong("recruiter_id"))
            .recruiterName(rs.getString("recruiter_name"))
            .status(ApplicationStatus.fromValue(rs.getString("status")))
            .createdAt(Rows.getTime(rs, "created_at"));

    private final JdbcClient jdbc;

    public ApplicationDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Application> findVisible(AuthUser user) {
        return jdbc.sql(SQL + " WHERE " + Visibility.application(user, "a") + " ORDER BY a.created_at DESC")
                .param("uid", user.id()).query(APPLICATION).list();
    }

    public Optional<Application> findVisible(AuthUser user, long id) {
        return jdbc.sql(SQL + " WHERE a.id = :id AND " + Visibility.application(user, "a"))
                .param("id", id).param("uid", user.id()).query(APPLICATION).optional();
    }

    public List<Application> findVisibleByCandidate(AuthUser user, long candidateId) {
        return jdbc.sql(SQL + " WHERE a.candidate_id = :candidateId AND " + Visibility.application(user, "a")
                        + " ORDER BY a.created_at DESC")
                .param("candidateId", candidateId).param("uid", user.id()).query(APPLICATION).list();
    }

    public Optional<Application> findById(long id) {
        return jdbc.sql(SQL + " WHERE a.id = :id").param("id", id).query(APPLICATION).optional();
    }

    public boolean activeExists(long candidateId, long funnelId) {
        return jdbc.sql("""
                        SELECT count(*) FROM applications
                        WHERE candidate_id = :candidateId AND funnel_id = :funnelId AND status = 'ACTIVE'""")
                .param("candidateId", candidateId).param("funnelId", funnelId).query(Long.class).single() > 0;
    }

    public long create(long candidateId, long funnelId, long recruiterId) {
        return jdbc.sql("""
                        INSERT INTO applications (candidate_id, funnel_id, recruiter_id)
                        VALUES (:candidateId, :funnelId, :recruiterId) RETURNING id""")
                .param("candidateId", candidateId).param("funnelId", funnelId).param("recruiterId", recruiterId)
                .query(Long.class).single();
    }

    public void updateStatus(long id, ApplicationStatus status) {
        jdbc.sql("UPDATE applications SET status = :status WHERE id = :id")
                .param("status", status.getValue()).param("id", id).update();
    }

    /** Number of interviews in each application's funnel. */
    public Map<Long, Integer> funnelSizes(List<Long> applicationIds) {
        if (applicationIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                        SELECT a.id, count(fd.id) AS size FROM applications a
                        LEFT JOIN funnel_details fd ON fd.funnel_id = a.funnel_id
                        WHERE a.id IN (:ids) GROUP BY a.id""")
                .param("ids", applicationIds)
                .query((rs, n) -> Map.entry(rs.getLong("id"), rs.getInt("size")))
                .list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public boolean funnelHasInterviewType(long applicationId, long interviewTypeId) {
        return jdbc.sql("""
                        SELECT count(*) FROM applications a JOIN funnel_details fd ON fd.funnel_id = a.funnel_id
                        WHERE a.id = :id AND fd.interviewtype_id = :typeId""")
                .param("id", applicationId).param("typeId", interviewTypeId).query(Long.class).single() > 0;
    }
}
