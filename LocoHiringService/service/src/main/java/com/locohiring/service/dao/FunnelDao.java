package com.locohiring.service.dao;

import com.locohiring.api.model.Funnel;
import com.locohiring.api.model.InterviewType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class FunnelDao {

    private static final String SQL = """
            SELECT f.id, f.name, f.description, f.recruiter_id, concat_ws(' ', r.first_name, r.surname) AS recruiter_name,
                   it.id AS it_id, it.name AS it_name, it.description AS it_description
            FROM funnels f
            JOIN recruiters r ON r.id = f.recruiter_id
            LEFT JOIN funnel_details fd ON fd.funnel_id = f.id
            LEFT JOIN interview_types it ON it.id = fd.interviewtype_id
            """;

    private final JdbcClient jdbc;

    public FunnelDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Funnel> findAll() {
        return query(jdbc.sql(SQL + " ORDER BY f.name, fd.id"));
    }

    public Optional<Funnel> findById(long id) {
        return query(jdbc.sql(SQL + " WHERE f.id = :id ORDER BY fd.id").param("id", id)).stream().findFirst();
    }

    public boolean nameExists(String name) {
        return jdbc.sql("SELECT count(*) FROM funnels WHERE lower(name) = lower(:name)")
                .param("name", name).query(Long.class).single() > 0;
    }

    public long create(String name, String description, long recruiterId, List<Long> interviewTypeIds) {
        long id = jdbc.sql("""
                        INSERT INTO funnels (name, description, recruiter_id)
                        VALUES (:name, :description, :recruiterId) RETURNING id""")
                .param("name", name).param("description", description).param("recruiterId", recruiterId)
                .query(Long.class).single();
        for (Long typeId : interviewTypeIds) {
            jdbc.sql("INSERT INTO funnel_details (funnel_id, interviewtype_id) VALUES (:funnelId, :typeId)")
                    .param("funnelId", id).param("typeId", typeId).update();
        }
        return id;
    }

    private List<Funnel> query(JdbcClient.StatementSpec statement) {
        Map<Long, Funnel> funnels = new LinkedHashMap<>();
        statement.query(rs -> {
            Funnel funnel = funnels.computeIfAbsent(rs.getLong("id"), id -> new Funnel().interviewTypes(new ArrayList<>()));
            funnel.id(rs.getLong("id"))
                    .name(rs.getString("name"))
                    .description(rs.getString("description"))
                    .recruiterId(rs.getLong("recruiter_id"))
                    .recruiterName(rs.getString("recruiter_name"));
            Long typeId = Rows.getLong(rs, "it_id");
            if (typeId != null) {
                funnel.getInterviewTypes().add(new InterviewType()
                        .id(typeId).name(rs.getString("it_name")).description(rs.getString("it_description")));
            }
        });
        return new ArrayList<>(funnels.values());
    }
}
