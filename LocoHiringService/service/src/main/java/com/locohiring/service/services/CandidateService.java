package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.Candidate;
import com.locohiring.api.model.CandidateCreate;
import com.locohiring.api.model.Resume;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.CandidateDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Candidates are only visible to the people involved with them; anything else looks like "not found". */
@Service
public class CandidateService {

    private final CandidateDao candidateDao;
    private final ApplicationDao applicationDao;
    private final ReferenceDataDao referenceDataDao;
    private final EventDao eventDao;

    public CandidateService(CandidateDao candidateDao, ApplicationDao applicationDao,
                            ReferenceDataDao referenceDataDao, EventDao eventDao) {
        this.candidateDao = candidateDao;
        this.applicationDao = applicationDao;
        this.referenceDataDao = referenceDataDao;
        this.eventDao = eventDao;
    }

    public List<Candidate> list(AuthUser user) {
        return candidateDao.findVisible(user);
    }

    public Candidate get(AuthUser user, long id) {
        return candidateDao.findVisible(user, id).orElseThrow(() -> ApiException.notFound("Candidate " + id));
    }

    @Transactional
    public Candidate create(AuthUser user, CandidateCreate request) {
        if (candidateDao.emailExists(request.getEmail())) {
            throw ApiException.conflict("A candidate with email " + request.getEmail() + " already exists");
        }
        if (referenceDataDao.findPosition(request.getPositionId()).isEmpty()) {
            throw ApiException.badRequest("Position " + request.getPositionId() + " does not exist");
        }
        long id = candidateDao.create(request.getEmail().trim(), request.getFirstName().trim(),
                request.getSurname() == null || request.getSurname().isBlank() ? null : request.getSurname().trim(),
                request.getPositionId());
        eventDao.record(user.email(), null, null, "Registered candidate " + request.getEmail() + " (id " + id + ")");
        return candidateDao.findById(id).orElseThrow();
    }

    public List<Application> listApplications(AuthUser user, long candidateId) {
        get(user, candidateId);
        return applicationDao.findVisibleByCandidate(user, candidateId);
    }

    public Resume getResume(AuthUser user, long candidateId) {
        get(user, candidateId);
        return candidateDao.findResume(candidateId).orElseThrow(() -> ApiException.notFound("Resume"));
    }

    @Transactional
    public void uploadResume(AuthUser user, long candidateId, Resume resume) {
        Candidate candidate = get(user, candidateId);
        candidateDao.updateResume(candidateId, resume);
        eventDao.record(user.email(), null, null,
                "Uploaded resume '" + resume.getFilename() + "' for candidate " + candidate.getEmail());
    }
}
