package com.locohiring.web.auth;

import com.locohiring.client.model.CurrentUser;
import com.locohiring.client.model.TokenResponse;
import com.locohiring.client.model.UserType;

import java.io.Serializable;
import java.time.Instant;

/** The logged-in user and their tokens, kept in the HTTP session. */
public record WebUser(long id, String email, String name, boolean recruiter, boolean interviewer, boolean admin,
                      String accessToken, String refreshToken, Instant accessTokenExpiresAt) implements Serializable {

    public static final String SESSION_KEY = "webUser";

    public static WebUser from(TokenResponse tokens) {
        CurrentUser user = tokens.getUser();
        String name = user.getSurname() == null ? user.getFirstName() : user.getFirstName() + " " + user.getSurname();
        return new WebUser(user.getId(), user.getEmail(), name, user.getType() == UserType.RECRUITER,
                user.getType() == UserType.INTERVIEWER, Boolean.TRUE.equals(user.getAdmin()),
                tokens.getAccessToken(), tokens.getRefreshToken(),
                Instant.now().plusSeconds(tokens.getExpiresIn()));
    }

    /** Admins get both the recruiter and the interviewer experience. */
    public boolean seesRecruiterPages() {
        return recruiter || admin;
    }

    public boolean seesInterviewerPages() {
        return interviewer || admin;
    }
}
