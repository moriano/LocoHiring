package com.locohiring.service.security;

import com.locohiring.api.model.UserType;

/** The authenticated caller: a row of the recruiters or interviewers table. */
public record AuthUser(long id, String email, UserType type, boolean admin) {

    public boolean isRecruiter() {
        return type == UserType.RECRUITER;
    }

    public boolean isInterviewer() {
        return type == UserType.INTERVIEWER;
    }
}
