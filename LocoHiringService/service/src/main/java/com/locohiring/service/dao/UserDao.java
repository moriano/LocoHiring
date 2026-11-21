package com.locohiring.service.dao;

import com.locohiring.api.model.CurrentUser;
import com.locohiring.api.model.Recruiter;
import com.locohiring.api.model.UserType;
import com.locohiring.service.security.AuthUser;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** Login data of recruiters and interviewers; an email identifies a user in either table. */
@Repository
public class UserDao {

    public record StoredUser(CurrentUser user, String passwordHash, boolean active) {
        public AuthUser toAuthUser() {
            return new AuthUser(user.getId(), user.getEmail(), user.getType(), user.getAdmin());
        }
    }

    private static final String SQL = """
            SELECT id, email, password, first_name, surname, is_admin, active, 'RECRUITER' AS type FROM recruiters
            UNION ALL
            SELECT id, email, password, first_name, surname, is_admin, active, 'INTERVIEWER' AS type FROM interviewers
            """;

    private static final RowMapper<Recruiter> RECRUITER = (rs, n) -> new Recruiter()
            .id(rs.getLong("id"))
            .email(rs.getString("email"))
            .firstName(rs.getString("first_name"))
            .surname(rs.getString("surname"))
            .admin(rs.getBoolean("is_admin"))
            .active(rs.getBoolean("active"));

    private final JdbcClient jdbc;

    public UserDao(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<StoredUser> findByEmail(String email) {
        return jdbc.sql("SELECT * FROM (" + SQL + ") u WHERE lower(email) = lower(:email)")
                .param("email", email)
                .query((rs, n) -> new StoredUser(new CurrentUser()
                        .id(rs.getLong("id"))
                        .email(rs.getString("email"))
                        .firstName(rs.getString("first_name"))
                        .surname(rs.getString("surname"))
                        .type(UserType.fromValue(rs.getString("type")))
                        .admin(rs.getBoolean("is_admin")), rs.getString("password"), rs.getBoolean("active")))
                .optional();
    }

    public List<Recruiter> listRecruiters() {
        return jdbc.sql("SELECT * FROM recruiters ORDER BY first_name, surname").query(RECRUITER).list();
    }

    public Recruiter createRecruiter(String email, String passwordHash, String firstName, String surname,
                                     boolean admin) {
        return jdbc.sql("""
                        INSERT INTO recruiters (email, password, first_name, surname, is_admin)
                        VALUES (:email, :password, :firstName, :surname, :admin) RETURNING *""")
                .param("email", email).param("password", passwordHash).param("firstName", firstName)
                .param("surname", surname).param("admin", admin)
                .query(RECRUITER).single();
    }

    public Optional<Recruiter> findRecruiter(long id) {
        return jdbc.sql("SELECT * FROM recruiters WHERE id = :id").param("id", id).query(RECRUITER).optional();
    }

    /** A null passwordHash keeps the current password. */
    public void updateRecruiter(long id, String email, String passwordHash, String firstName, String surname,
                                boolean admin, boolean active) {
        jdbc.sql("""
                        UPDATE recruiters SET email = :email, password = coalesce(:password, password),
                               first_name = :firstName, surname = :surname, is_admin = :admin, active = :active
                        WHERE id = :id""")
                .param("email", email).param("password", passwordHash).param("firstName", firstName)
                .param("surname", surname).param("admin", admin).param("active", active).param("id", id)
                .update();
    }

    public boolean recruiterExists(long id) {
        return jdbc.sql("SELECT count(*) FROM recruiters WHERE id = :id").param("id", id)
                .query(Long.class).single() > 0;
    }
}
