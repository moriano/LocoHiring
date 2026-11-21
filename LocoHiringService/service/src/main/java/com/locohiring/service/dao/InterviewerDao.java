package com.locohiring.service.dao;

import com.locohiring.api.model.Interviewer;
import com.locohiring.api.model.JobFamily;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class InterviewerDao {

    private static final String SQL = """
            SELECT iv.id, iv.email, iv.first_name, iv.surname, iv.level, iv.bar_raiser, iv.is_admin, iv.active,
                   jf.id AS jf_id, jf.name AS jf_name
            FROM interviewers iv
            LEFT JOIN interviewers_jobfamily ij ON ij.interviewer_id = iv.id
            LEFT JOIN job_families jf ON jf.id = ij.jobfamily_id
            """;

    private final JdbcClient jdbc;

    public InterviewerDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Interviewer> findAll() {
        return query(jdbc.sql(SQL + " ORDER BY iv.first_name, iv.surname, jf.name"));
    }

    public Optional<Interviewer> findById(long id) {
        return query(jdbc.sql(SQL + " WHERE iv.id = :id ORDER BY jf.name").param("id", id)).stream().findFirst();
    }

    public long create(String email, String passwordHash, String firstName, String surname, int level,
                       boolean barRaiser, boolean admin, List<Long> jobFamilyIds) {
        long id = jdbc.sql("""
                        INSERT INTO interviewers (email, password, first_name, surname, level, bar_raiser, is_admin)
                        VALUES (:email, :password, :firstName, :surname, :level, :barRaiser, :admin) RETURNING id""")
                .param("email", email).param("password", passwordHash).param("firstName", firstName)
                .param("surname", surname).param("level", level).param("barRaiser", barRaiser).param("admin", admin)
                .query(Long.class).single();
        insertJobFamilies(id, jobFamilyIds);
        return id;
    }

    /** A null passwordHash keeps the current password. The job families are replaced. */
    public void update(long id, String email, String passwordHash, String firstName, String surname, int level,
                       boolean barRaiser, boolean admin, boolean active, List<Long> jobFamilyIds) {
        jdbc.sql("""
                        UPDATE interviewers SET email = :email, password = coalesce(:password, password),
                               first_name = :firstName, surname = :surname, level = :level, bar_raiser = :barRaiser,
                               is_admin = :admin, active = :active
                        WHERE id = :id""")
                .param("email", email).param("password", passwordHash).param("firstName", firstName)
                .param("surname", surname).param("level", level).param("barRaiser", barRaiser).param("admin", admin)
                .param("active", active).param("id", id)
                .update();
        jdbc.sql("DELETE FROM interviewers_jobfamily WHERE interviewer_id = :id").param("id", id).update();
        insertJobFamilies(id, jobFamilyIds);
    }

    private void insertJobFamilies(long interviewerId, List<Long> jobFamilyIds) {
        for (Long jobFamilyId : jobFamilyIds) {
            jdbc.sql("INSERT INTO interviewers_jobfamily (interviewer_id, jobfamily_id) VALUES (:id, :jobFamilyId)")
                    .param("id", interviewerId).param("jobFamilyId", jobFamilyId).update();
        }
    }

    private List<Interviewer> query(JdbcClient.StatementSpec statement) {
        Map<Long, Interviewer> interviewers = new LinkedHashMap<>();
        statement.query(rs -> {
            Interviewer interviewer = interviewers.computeIfAbsent(rs.getLong("id"),
                    id -> new Interviewer().jobFamilies(new ArrayList<>()));
            interviewer.id(rs.getLong("id"))
                    .email(rs.getString("email"))
                    .firstName(rs.getString("first_name"))
                    .surname(rs.getString("surname"))
                    .level(rs.getInt("level"))
                    .barRaiser(rs.getBoolean("bar_raiser"))
                    .admin(rs.getBoolean("is_admin"))
                    .active(rs.getBoolean("active"));
            Long familyId = Rows.getLong(rs, "jf_id");
            if (familyId != null) {
                interviewer.getJobFamilies().add(new JobFamily().id(familyId).name(rs.getString("jf_name")));
            }
        });
        return new ArrayList<>(interviewers.values());
    }
}
