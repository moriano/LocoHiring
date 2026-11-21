package com.locohiring.service.dao;

import com.locohiring.api.model.InterviewType;
import com.locohiring.api.model.JobFamily;
import com.locohiring.api.model.Position;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Job families, positions and interview types. */
@Repository
public class ReferenceDataDao {

    private static final RowMapper<JobFamily> JOB_FAMILY = (rs, n) ->
            new JobFamily().id(rs.getLong("id")).name(rs.getString("name"));

    private static final RowMapper<InterviewType> INTERVIEW_TYPE = (rs, n) -> new InterviewType()
            .id(rs.getLong("id")).name(rs.getString("name")).description(rs.getString("description"));

    private static final RowMapper<Position> POSITION = (rs, n) -> new Position()
            .id(rs.getLong("id"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .level(rs.getInt("level"))
            .jobFamilyId(rs.getLong("jobfamily_id"))
            .jobFamilyName(rs.getString("jobfamily_name"));

    private static final String POSITION_SQL = """
            SELECT p.*, jf.name AS jobfamily_name
            FROM positions p JOIN job_families jf ON jf.id = p.jobfamily_id
            """;

    private final JdbcClient jdbc;

    public ReferenceDataDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<JobFamily> listJobFamilies() {
        return jdbc.sql("SELECT * FROM job_families ORDER BY name").query(JOB_FAMILY).list();
    }

    public boolean jobFamilyNameExists(String name) {
        return jdbc.sql("SELECT count(*) FROM job_families WHERE lower(name) = lower(:name)")
                .param("name", name).query(Long.class).single() > 0;
    }

    public JobFamily createJobFamily(String name) {
        return jdbc.sql("INSERT INTO job_families (name) VALUES (:name) RETURNING *")
                .param("name", name).query(JOB_FAMILY).single();
    }

    public boolean jobFamilyExists(long id) {
        return jdbc.sql("SELECT count(*) FROM job_families WHERE id = :id").param("id", id)
                .query(Long.class).single() > 0;
    }

    public List<Position> listPositions() {
        return jdbc.sql(POSITION_SQL + " ORDER BY p.name").query(POSITION).list();
    }

    public Optional<Position> findPosition(long id) {
        return jdbc.sql(POSITION_SQL + " WHERE p.id = :id").param("id", id).query(POSITION).optional();
    }

    /** The position the candidate of the interview applies for. */
    public Position findPositionOfInterview(long interviewId) {
        return jdbc.sql(POSITION_SQL + """
                         JOIN candidates c ON c.position_id = p.id
                         JOIN applications a ON a.candidate_id = c.id
                         JOIN interviews i ON i.application_id = a.id
                        WHERE i.id = :id""")
                .param("id", interviewId).query(POSITION).single();
    }

    /** The position the candidate of the application applies for. */
    public Position findPositionOfApplication(long applicationId) {
        return jdbc.sql(POSITION_SQL + """
                         JOIN candidates c ON c.position_id = p.id
                         JOIN applications a ON a.candidate_id = c.id
                        WHERE a.id = :id""")
                .param("id", applicationId).query(POSITION).single();
    }

    public long createPosition(String name, String description, int level, long jobFamilyId) {
        return jdbc.sql("""
                        INSERT INTO positions (name, description, level, jobfamily_id)
                        VALUES (:name, :description, :level, :jobFamilyId) RETURNING id""")
                .param("name", name).param("description", description).param("level", level)
                .param("jobFamilyId", jobFamilyId)
                .query(Long.class).single();
    }

    public List<InterviewType> listInterviewTypes() {
        return jdbc.sql("SELECT * FROM interview_types ORDER BY name").query(INTERVIEW_TYPE).list();
    }

    public boolean interviewTypeNameExists(String name) {
        return jdbc.sql("SELECT count(*) FROM interview_types WHERE lower(name) = lower(:name)")
                .param("name", name).query(Long.class).single() > 0;
    }

    public InterviewType createInterviewType(String name, String description) {
        return jdbc.sql("INSERT INTO interview_types (name, description) VALUES (:name, :description) RETURNING *")
                .param("name", name).param("description", description).query(INTERVIEW_TYPE).single();
    }

    public List<InterviewType> findInterviewTypes(List<Long> ids) {
        return jdbc.sql("SELECT * FROM interview_types WHERE id IN (:ids)").param("ids", ids)
                .query(INTERVIEW_TYPE).list();
    }
}
