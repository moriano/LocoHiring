package com.locohiring.service.dao;

import com.locohiring.service.security.AuthUser;

/**
 * SQL predicates that restrict rows to what the caller may see. Each takes the parameter :uid (the caller's id).
 * <ul>
 *   <li>Admins see everything.</li>
 *   <li>Recruiters see the applications they own, and candidates of those applications or with no application.</li>
 *   <li>Interviewers see the applications (and candidates) where they are the interviewer or shadow of an interview.</li>
 * </ul>
 */
final class Visibility {

    private Visibility() {
    }

    /** Predicate on an applications row aliased {@code alias}. */
    static String application(AuthUser user, String alias) {
        if (user.admin()) {
            return "TRUE";
        }
        if (user.isRecruiter()) {
            return alias + ".recruiter_id = :uid";
        }
        return "EXISTS (SELECT 1 FROM interviews vi WHERE vi.application_id = " + alias + ".id"
                + " AND (vi.interviewer_id = :uid OR vi.shadow_id = :uid))";
    }

    /** Predicate on a candidates row aliased {@code alias}. */
    static String candidate(AuthUser user, String alias) {
        if (user.admin()) {
            return "TRUE";
        }
        if (user.isRecruiter()) {
            return "(NOT EXISTS (SELECT 1 FROM applications va WHERE va.candidate_id = " + alias + ".id)"
                    + " OR EXISTS (SELECT 1 FROM applications va WHERE va.candidate_id = " + alias + ".id"
                    + " AND va.recruiter_id = :uid))";
        }
        return "EXISTS (SELECT 1 FROM applications va JOIN interviews vi ON vi.application_id = va.id"
                + " WHERE va.candidate_id = " + alias + ".id AND (vi.interviewer_id = :uid OR vi.shadow_id = :uid))";
    }
}
