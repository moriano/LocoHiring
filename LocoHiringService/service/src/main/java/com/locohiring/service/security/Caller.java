package com.locohiring.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Gives controllers the caller set by {@link JwtAuthenticationFilter}. */
public final class Caller {

    public static final String RECRUITER = "hasAnyRole('RECRUITER','ADMIN')";
    public static final String INTERVIEWER = "hasAnyRole('INTERVIEWER','ADMIN')";
    public static final String ADMIN = "hasRole('ADMIN')";

    private Caller() {
    }

    public static AuthUser get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (AuthUser) authentication.getPrincipal();
    }
}
