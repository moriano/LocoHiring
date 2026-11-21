package com.locohiring.service.services;

import com.locohiring.api.model.Interviewer;
import com.locohiring.api.model.InterviewerCreate;
import com.locohiring.api.model.InterviewerUpdate;
import com.locohiring.api.model.Recruiter;
import com.locohiring.api.model.RecruiterCreate;
import com.locohiring.api.model.RecruiterUpdate;
import com.locohiring.api.model.UserType;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.InterviewerDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.dao.UserDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Recruiter and interviewer accounts. An email identifies one user across both tables, since it is the login. */
@Service
public class UserService {

    private final UserDao userDao;
    private final InterviewerDao interviewerDao;
    private final ReferenceDataDao referenceDataDao;
    private final EventDao eventDao;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserDao userDao, InterviewerDao interviewerDao, ReferenceDataDao referenceDataDao,
                       EventDao eventDao, PasswordEncoder passwordEncoder) {
        this.userDao = userDao;
        this.interviewerDao = interviewerDao;
        this.referenceDataDao = referenceDataDao;
        this.eventDao = eventDao;
        this.passwordEncoder = passwordEncoder;
    }

    public List<Recruiter> listRecruiters() {
        return userDao.listRecruiters();
    }

    @Transactional
    public Recruiter createRecruiter(AuthUser user, RecruiterCreate request) {
        String email = requireUnusedEmail(request.getEmail());
        boolean admin = Boolean.TRUE.equals(request.getAdmin());
        Recruiter created = userDao.createRecruiter(email, passwordEncoder.encode(request.getPassword()),
                request.getFirstName().trim(), blankToNull(request.getSurname()), admin);
        eventDao.record(user.email(), null, null, "Created recruiter " + email + (admin ? " (admin)" : ""));
        return created;
    }

    @Transactional
    public Interviewer createInterviewer(AuthUser user, InterviewerCreate request) {
        String email = requireUnusedEmail(request.getEmail());
        List<Long> jobFamilyIds = requireJobFamilies(request.getJobFamilyIds());
        boolean admin = Boolean.TRUE.equals(request.getAdmin());
        long id = interviewerDao.create(email, passwordEncoder.encode(request.getPassword()),
                request.getFirstName().trim(), blankToNull(request.getSurname()), request.getLevel(),
                Boolean.TRUE.equals(request.getBarRaiser()), admin, jobFamilyIds);
        eventDao.record(user.email(), null, null, "Created interviewer " + email + " (level " + request.getLevel()
                + (admin ? ", admin" : "") + ")");
        return interviewerDao.findById(id).orElseThrow();
    }

    @Transactional
    public Recruiter updateRecruiter(AuthUser user, long id, RecruiterUpdate request) {
        Recruiter current = userDao.findRecruiter(id).orElseThrow(() -> ApiException.notFound("Recruiter " + id));
        String email = requireUnusedEmail(request.getEmail(), UserType.RECRUITER, id);
        requireNotLockingOutSelf(user, UserType.RECRUITER, id, request.getAdmin(), request.getActive());
        userDao.updateRecruiter(id, email, encodeIfGiven(request.getPassword()), request.getFirstName().trim(),
                blankToNull(request.getSurname()), request.getAdmin(), request.getActive());
        eventDao.record(user.email(), null, null, "Updated recruiter " + email
                + changes(current.getActive(), request.getActive(), current.getAdmin(), request.getAdmin(),
                request.getPassword()));
        return userDao.findRecruiter(id).orElseThrow();
    }

    @Transactional
    public Interviewer updateInterviewer(AuthUser user, long id, InterviewerUpdate request) {
        Interviewer current = interviewerDao.findById(id).orElseThrow(() -> ApiException.notFound("Interviewer " + id));
        String email = requireUnusedEmail(request.getEmail(), UserType.INTERVIEWER, id);
        requireNotLockingOutSelf(user, UserType.INTERVIEWER, id, request.getAdmin(), request.getActive());
        List<Long> jobFamilyIds = requireJobFamilies(request.getJobFamilyIds());
        interviewerDao.update(id, email, encodeIfGiven(request.getPassword()), request.getFirstName().trim(),
                blankToNull(request.getSurname()), request.getLevel(), request.getBarRaiser(), request.getAdmin(),
                request.getActive(), jobFamilyIds);
        String level = current.getLevel().equals(request.getLevel()) ? ""
                : "; level " + current.getLevel() + " -> " + request.getLevel();
        eventDao.record(user.email(), null, null, "Updated interviewer " + email + level
                + changes(current.getActive(), request.getActive(), current.getAdmin(), request.getAdmin(),
                request.getPassword()));
        return interviewerDao.findById(id).orElseThrow();
    }

    /** An admin who deactivates themselves or drops their own admin flag could lock everyone out. */
    private static void requireNotLockingOutSelf(AuthUser user, UserType type, long id, boolean admin, boolean active) {
        if (user.type() == type && user.id() == id && (!admin || !active)) {
            throw ApiException.badRequest("You cannot deactivate yourself or remove your own admin rights");
        }
    }

    private static String changes(boolean wasActive, boolean active, boolean wasAdmin, boolean admin, String password) {
        StringBuilder changes = new StringBuilder();
        if (wasActive != active) {
            changes.append(active ? "; reactivated" : "; deactivated");
        }
        if (wasAdmin != admin) {
            changes.append(admin ? "; made admin" : "; admin rights removed");
        }
        if (password != null && !password.isBlank()) {
            changes.append("; password changed");
        }
        return changes.toString();
    }

    private String encodeIfGiven(String password) {
        return password == null || password.isBlank() ? null : passwordEncoder.encode(password);
    }

    private List<Long> requireJobFamilies(List<Long> requested) {
        List<Long> jobFamilyIds = requested.stream().distinct().toList();
        for (Long jobFamilyId : jobFamilyIds) {
            if (!referenceDataDao.jobFamilyExists(jobFamilyId)) {
                throw ApiException.badRequest("Job family " + jobFamilyId + " does not exist");
            }
        }
        return jobFamilyIds;
    }

    private String requireUnusedEmail(String email) {
        return requireUnusedEmail(email, null, -1);
    }

    /** The email may already belong to the user being edited (same type and id), but to nobody else. */
    private String requireUnusedEmail(String email, UserType type, long id) {
        String trimmed = email.trim();
        userDao.findByEmail(trimmed)
                .filter(u -> !(u.user().getType() == type && u.user().getId() == id))
                .ifPresent(u -> {
                    throw ApiException.conflict("A recruiter or interviewer with email " + trimmed + " already exists");
                });
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
