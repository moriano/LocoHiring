package com.locohiring.service.dao;

import com.locohiring.api.model.Candidate;
import com.locohiring.api.model.Resume;
import com.locohiring.service.security.AuthUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CandidateDao {

    // Never selects the resume itself, which can be large.
    private static final String SQL = """
            SELECT c.id, c.email, c.first_name, c.surname, c.position_id, c.resume IS NOT NULL AS has_resume,
                   p.name AS position_name, p.level AS position_level, jf.name AS jobfamily_name
            FROM candidates c
            JOIN positions p ON p.id = c.position_id
            JOIN job_families jf ON jf.id = p.jobfamily_id
            """;

    private static final RowMapper<Candidate> CANDIDATE = (rs, n) -> new Candidate()
            .id(rs.getLong("id"))
            .email(rs.getString("email"))
            .firstName(rs.getString("first_name"))
            .surname(rs.getString("surname"))
            .positionId(rs.getLong("position_id"))
            .positionName(rs.getString("position_name"))
            .positionLevel(rs.getInt("position_level"))
            .jobFamilyName(rs.getString("jobfamily_name"))
            .hasResume(rs.getBoolean("has_resume"));

    private final JdbcClient jdbc;

    public CandidateDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Candidate> findVisible(AuthUser user) {
        return jdbc.sql(SQL + " WHERE " + Visibility.candidate(user, "c") + " ORDER BY c.first_name, c.surname")
                .param("uid", user.id()).query(CANDIDATE).list();
    }

    public Optional<Candidate> findVisible(AuthUser user, long id) {
        return jdbc.sql(SQL + " WHERE c.id = :id AND " + Visibility.candidate(user, "c"))
                .param("id", id).param("uid", user.id()).query(CANDIDATE).optional();
    }

    public Optional<Candidate> findById(long id) {
        return jdbc.sql(SQL + " WHERE c.id = :id").param("id", id).query(CANDIDATE).optional();
    }

    public boolean emailExists(String email) {
        return jdbc.sql("SELECT count(*) FROM candidates WHERE lower(email) = lower(:email)")
                .param("email", email).query(Long.class).single() > 0;
    }

    public long create(String email, String firstName, String surname, long positionId) {
        return jdbc.sql("""
                        INSERT INTO candidates (email, first_name, surname, position_id)
                        VALUES (:email, :firstName, :surname, :positionId) RETURNING id""")
                .param("email", email).param("firstName", firstName).param("surname", surname)
                .param("positionId", positionId)
                .query(Long.class).single();
    }

    public Optional<Resume> findResume(long candidateId) {
        return jdbc.sql("""
                        SELECT resume, resume_filename, resume_content_type FROM candidates
                        WHERE id = :id AND resume IS NOT NULL""")
                .param("id", candidateId)
                .query((rs, n) -> new Resume()
                        .content(rs.getBytes("resume"))
                        .filename(rs.getString("resume_filename"))
                        .contentType(rs.getString("resume_content_type")))
                .optional();
    }

    public void updateResume(long candidateId, Resume resume) {
        jdbc.sql("""
                        UPDATE candidates SET resume = :content, resume_filename = :filename,
                               resume_content_type = :contentType
                        WHERE id = :id""")
                .param("content", resume.getContent()).param("filename", resume.getFilename())
                .param("contentType", resume.getContentType()).param("id", candidateId)
                .update();
    }
}
