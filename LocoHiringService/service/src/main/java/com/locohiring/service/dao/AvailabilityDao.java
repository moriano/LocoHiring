package com.locohiring.service.dao;

import com.locohiring.api.model.AvailabilityBlock;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** One-hour availability blocks; a block is booked when its interview_id is set. */
@Repository
public class AvailabilityDao {

    private static final String SQL = """
            SELECT ca.id, ca.interviewer_id, ca.interview_id, ca.date, ca.start_availability, ca.end_availability,
                   concat_ws(' ', c.first_name, c.surname) AS candidate_name, it.name AS interviewtype_name
            FROM calendar_availability ca
            LEFT JOIN interviews i ON i.id = ca.interview_id
            LEFT JOIN applications a ON a.id = i.application_id
            LEFT JOIN candidates c ON c.id = a.candidate_id
            LEFT JOIN interview_types it ON it.id = i.interviewtype_id
            """;

    private static final RowMapper<AvailabilityBlock> BLOCK = (rs, n) -> {
        Long interviewId = Rows.getLong(rs, "interview_id");
        return new AvailabilityBlock()
                .id(rs.getLong("id"))
                .interviewerId(rs.getLong("interviewer_id"))
                .date(Rows.getDate(rs, "date"))
                .startHour(rs.getInt("start_availability"))
                .endHour(rs.getInt("end_availability"))
                .interviewId(interviewId)
                .candidateName(interviewId == null ? null : rs.getString("candidate_name"))
                .interviewTypeName(rs.getString("interviewtype_name"));
    };

    /** A free block: the interviewer is available from {@code hour} to {@code hour + 1} on {@code date}. */
    public record FreeBlock(long interviewerId, LocalDate date, int hour) {
    }

    private final JdbcClient jdbc;

    public AvailabilityDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<AvailabilityBlock> findByInterviewer(long interviewerId, LocalDate from, LocalDate to) {
        return jdbc.sql(SQL + """
                         WHERE ca.interviewer_id = :interviewerId AND ca.date BETWEEN :from AND :to
                        ORDER BY ca.date, ca.start_availability""")
                .param("interviewerId", interviewerId).param("from", from).param("to", to)
                .query(BLOCK).list();
    }

    public Optional<AvailabilityBlock> findById(long id) {
        return jdbc.sql(SQL + " WHERE ca.id = :id").param("id", id).query(BLOCK).optional();
    }

    /** Inserts the block unless the interviewer already has one at that hour. */
    public Optional<AvailabilityBlock> createIfAbsent(long interviewerId, LocalDate date, int startHour) {
        return jdbc.sql("""
                        INSERT INTO calendar_availability (interviewer_id, date, start_availability, end_availability)
                        VALUES (:interviewerId, :date, :start, :start + 1)
                        ON CONFLICT (interviewer_id, date, start_availability) DO NOTHING
                        RETURNING id, interviewer_id, interview_id, date, start_availability, end_availability,
                                  NULL AS candidate_name, NULL AS interviewtype_name""")
                .param("interviewerId", interviewerId).param("date", date).param("start", startHour)
                .query(BLOCK).optional();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM calendar_availability WHERE id = :id").param("id", id).update();
    }

    /**
     * The interviewer's block at that hour if it is free, or already booked by {@code interviewId}.
     */
    public Optional<Long> findFreeBlock(long interviewerId, LocalDate date, int hour, Long interviewId) {
        return jdbc.sql("""
                        SELECT id FROM calendar_availability
                        WHERE interviewer_id = :interviewerId AND date = :date AND start_availability = :hour
                          AND (interview_id IS NULL OR interview_id = :interviewId)""")
                .param("interviewerId", interviewerId).param("date", date).param("hour", hour)
                .param("interviewId", interviewId == null ? -1L : interviewId)
                .query(Long.class).optional();
    }

    /** Free blocks of every interviewer at that hour, as (interviewer id, block id). */
    public List<long[]> findFreeBlocks(LocalDate date, int hour, Long interviewId) {
        return jdbc.sql("""
                        SELECT interviewer_id, id FROM calendar_availability
                        WHERE date = :date AND start_availability = :hour
                          AND (interview_id IS NULL OR interview_id = :interviewId)
                        ORDER BY interviewer_id""")
                .param("date", date).param("hour", hour).param("interviewId", interviewId == null ? -1L : interviewId)
                .query((rs, n) -> new long[]{rs.getLong("interviewer_id"), rs.getLong("id")})
                .list();
    }

    /** Every interviewer's unbooked blocks between from and to, in time order. */
    public List<FreeBlock> findFreeBlocksBetween(LocalDate from, LocalDate to) {
        return jdbc.sql("""
                        SELECT interviewer_id, date, start_availability FROM calendar_availability
                        WHERE interview_id IS NULL AND date BETWEEN :from AND :to
                        ORDER BY date, start_availability, interviewer_id""")
                .param("from", from).param("to", to)
                .query((rs, n) -> new FreeBlock(rs.getLong("interviewer_id"), Rows.getDate(rs, "date"),
                        rs.getInt("start_availability")))
                .list();
    }

    public void book(long blockId, long interviewId) {
        jdbc.sql("UPDATE calendar_availability SET interview_id = :interviewId WHERE id = :id")
                .param("interviewId", interviewId).param("id", blockId).update();
    }

    public void releaseInterview(long interviewId) {
        jdbc.sql("UPDATE calendar_availability SET interview_id = NULL WHERE interview_id = :interviewId")
                .param("interviewId", interviewId).update();
    }
}
