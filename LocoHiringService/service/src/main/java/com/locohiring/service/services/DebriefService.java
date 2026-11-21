package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.DebriefUpdate;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.DebriefDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class DebriefService {

    private final DebriefDao debriefDao;
    private final ApplicationDao applicationDao;
    private final InterviewDao interviewDao;
    private final EventDao eventDao;
    private final Clock clock;

    public DebriefService(DebriefDao debriefDao, ApplicationDao applicationDao, InterviewDao interviewDao,
                          EventDao eventDao, Clock clock) {
        this.debriefDao = debriefDao;
        this.applicationDao = applicationDao;
        this.interviewDao = interviewDao;
        this.eventDao = eventDao;
        this.clock = clock;
    }

    @Transactional
    public Debrief create(AuthUser user, long applicationId, OffsetDateTime scheduledAt) {
        Application application = findOwned(user, applicationId);
        if (debriefDao.findByApplication(applicationId).isPresent()) {
            throw ApiException.conflict("The application already has a debrief");
        }
        debriefDao.create(applicationId, scheduledAt);
        eventDao.record(user.email(), application.getFunnelId(), applicationId, "Debrief scheduled at " + HiringRules.time(scheduledAt));
        return load(application);
    }

    /** Fields left out of the request keep their value. */
    @Transactional
    public Debrief update(AuthUser user, long applicationId, DebriefUpdate request) {
        Application application = findOwned(user, applicationId);
        Debrief current = debriefDao.findByApplication(applicationId)
                .orElseThrow(() -> ApiException.notFound("Debrief"));
        OffsetDateTime scheduledAt = request.getScheduledAt() != null ? request.getScheduledAt() : current.getScheduledAt();
        String notes = request.getNotes() != null ? request.getNotes() : current.getNotes();
        Boolean inclined = request.getInclined() != null ? request.getInclined() : current.getInclined();
        debriefDao.update(applicationId, scheduledAt, notes, inclined);
        StringBuilder message = new StringBuilder("Debrief updated");
        if (request.getScheduledAt() != null) {
            message.append("; scheduled at ").append(HiringRules.time(scheduledAt));
        }
        if (request.getNotes() != null) {
            message.append("; notes recorded");
        }
        if (request.getInclined() != null) {
            message.append("; decision: ").append(inclined ? "inclined" : "not inclined");
        }
        eventDao.record(user.email(), application.getFunnelId(), applicationId, message.toString());
        return load(application);
    }

    private Debrief load(Application application) {
        Debrief debrief = debriefDao.findByApplication(application.getId()).orElseThrow();
        int funnelSize = applicationDao.funnelSizes(List.of(application.getId())).getOrDefault(application.getId(), 0);
        return HiringRules.withDeadline(debrief, application.getStatus(), funnelSize,
                interviewDao.findByApplicationIds(List.of(application.getId())), OffsetDateTime.now(clock));
    }

    private Application findOwned(AuthUser user, long applicationId) {
        Application application = applicationDao.findVisible(user, applicationId)
                .orElseThrow(() -> ApiException.notFound("Application " + applicationId));
        HiringRules.requireOwner(user, application);
        return application;
    }
}
