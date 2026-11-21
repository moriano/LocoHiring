package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewStatus;
import com.locohiring.api.model.Interviewer;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.AvailabilityDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.dao.InterviewerDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Scheduling of interviews. Assigning an interviewer books one of their availability blocks (the one starting at
 * the interview time); unassigning, rescheduling and cancelling release it.
 */
@Service
public class InterviewService {

    private final InterviewDao interviewDao;
    private final ApplicationDao applicationDao;
    private final AvailabilityDao availabilityDao;
    private final InterviewerDao interviewerDao;
    private final ReferenceDataDao referenceDataDao;
    private final EventDao eventDao;
    private final Clock clock;

    public InterviewService(InterviewDao interviewDao, ApplicationDao applicationDao, AvailabilityDao availabilityDao,
                            InterviewerDao interviewerDao, ReferenceDataDao referenceDataDao, EventDao eventDao,
                            Clock clock) {
        this.interviewDao = interviewDao;
        this.applicationDao = applicationDao;
        this.availabilityDao = availabilityDao;
        this.interviewerDao = interviewerDao;
        this.referenceDataDao = referenceDataDao;
        this.eventDao = eventDao;
        this.clock = clock;
    }

    public Interview get(AuthUser user, long id) {
        Interview interview = interviewDao.findById(id).orElseThrow(() -> ApiException.notFound("Interview " + id));
        applicationDao.findVisible(user, interview.getApplicationId())
                .orElseThrow(() -> ApiException.notFound("Interview " + id));
        return interview;
    }

    @Transactional
    public Interview assign(AuthUser user, long id, long interviewerId, Long shadowId) {
        Interview interview = get(user, id);
        Application application = requireChangeable(user, interview);
        if (Objects.equals(interviewerId, shadowId)) {
            throw ApiException.badRequest("The shadow must be a different interviewer");
        }
        Interviewer interviewer = findInterviewer(interviewerId);
        Interviewer shadow = shadowId == null ? null : findInterviewer(shadowId);
        HiringRules.ineligibilityReason(interviewer, referenceDataDao.findPositionOfInterview(id)).ifPresent(reason -> {
            throw ApiException.badRequest(reason);
        });

        availabilityDao.releaseInterview(id);
        bookOrFail(interviewer, interview);
        if (shadow != null) {
            bookOrFail(shadow, interview);
        }
        interviewDao.update(id, interview.getScheduledAt(), interviewerId, shadowId, InterviewStatus.SCHEDULED);
        eventDao.record(user.email(), application.getFunnelId(), application.getId(),
                "Interview " + id + " assigned to " + HiringRules.name(interviewer)
                        + (shadow == null ? "" : " with shadow " + HiringRules.name(shadow)));
        return interviewDao.findById(id).orElseThrow();
    }

    @Transactional
    public Interview unassign(AuthUser user, long id) {
        Interview interview = get(user, id);
        Application application = requireChangeable(user, interview);
        availabilityDao.releaseInterview(id);
        interviewDao.update(id, interview.getScheduledAt(), null, null, InterviewStatus.LOOKING_FOR_INTERVIEWER);
        eventDao.record(user.email(), application.getFunnelId(), application.getId(),
                "Interviewers removed from interview " + id);
        return interviewDao.findById(id).orElseThrow();
    }

    /** Keeps each interviewer who has a free block at the new time; the others are removed. */
    @Transactional
    public Interview reschedule(AuthUser user, long id, OffsetDateTime scheduledAt) {
        Interview interview = get(user, id);
        Application application = requireChangeable(user, interview);
        availabilityDao.releaseInterview(id);
        Interview moved = new Interview().id(id).scheduledAt(scheduledAt);
        Long interviewerId = keepIfFree(interview.getInterviewerId(), moved);
        Long shadowId = keepIfFree(interview.getShadowId(), moved);
        InterviewStatus status = interviewerId == null ? InterviewStatus.LOOKING_FOR_INTERVIEWER : InterviewStatus.SCHEDULED;
        interviewDao.update(id, scheduledAt, interviewerId, shadowId, status);
        StringBuilder message = new StringBuilder("Interview " + id + " moved from " + HiringRules.time(interview.getScheduledAt())
                + " to " + HiringRules.time(scheduledAt));
        if (interview.getInterviewerId() != null && interviewerId == null) {
            message.append("; ").append(interview.getInterviewerName()).append(" is not available and was removed");
        }
        if (interview.getShadowId() != null && shadowId == null) {
            message.append("; shadow ").append(interview.getShadowName()).append(" is not available and was removed");
        }
        eventDao.record(user.email(), application.getFunnelId(), application.getId(), message.toString());
        return interviewDao.findById(id).orElseThrow();
    }

    @Transactional
    public Interview cancel(AuthUser user, long id) {
        Interview interview = get(user, id);
        Application application = requireChangeable(user, interview);
        availabilityDao.releaseInterview(id);
        interviewDao.updateStatus(id, InterviewStatus.CANCELLED);
        eventDao.record(user.email(), application.getFunnelId(), application.getId(), "Interview " + id + " cancelled");
        return interviewDao.findById(id).orElseThrow();
    }

    @Transactional
    public Interview complete(AuthUser user, long id) {
        Interview interview = get(user, id);
        Application application = applicationDao.findById(interview.getApplicationId()).orElseThrow();
        HiringRules.requireOwner(user, application);
        if (interview.getStatus() != InterviewStatus.SCHEDULED) {
            throw ApiException.badRequest("Only scheduled interviews can be completed; this one is " + interview.getStatus());
        }
        if (interview.getScheduledAt().isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.badRequest("The interview has not started yet");
        }
        interviewDao.updateStatus(id, InterviewStatus.COMPLETED);
        eventDao.record(user.email(), application.getFunnelId(), application.getId(), "Interview " + id + " completed");
        return interviewDao.findById(id).orElseThrow();
    }

    private Application requireChangeable(AuthUser user, Interview interview) {
        Application application = applicationDao.findById(interview.getApplicationId()).orElseThrow();
        HiringRules.requireOwner(user, application);
        if (interview.getStatus() == InterviewStatus.CANCELLED || interview.getStatus() == InterviewStatus.COMPLETED) {
            throw ApiException.badRequest("The interview is " + interview.getStatus());
        }
        if (HiringRules.hasEnded(interview, OffsetDateTime.now(clock))) {
            throw ApiException.badRequest("The interview has already taken place");
        }
        return application;
    }

    private Interviewer findInterviewer(long id) {
        Interviewer interviewer = interviewerDao.findById(id)
                .orElseThrow(() -> ApiException.badRequest("Interviewer " + id + " does not exist"));
        if (!interviewer.getActive()) {
            throw ApiException.badRequest(HiringRules.name(interviewer) + " is deactivated");
        }
        return interviewer;
    }

    private void bookOrFail(Interviewer interviewer, Interview interview) {
        LocalDateTime local = interview.getScheduledAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
        long blockId = availabilityDao.findFreeBlock(interviewer.getId(), local.toLocalDate(), local.getHour(),
                        interview.getId())
                .orElseThrow(() -> ApiException.badRequest(HiringRules.name(interviewer) + " has no free availability on "
                        + local.toLocalDate() + " at " + local.getHour() + ":00"));
        availabilityDao.book(blockId, interview.getId());
    }

    private Long keepIfFree(Long interviewerId, Interview moved) {
        if (interviewerId == null) {
            return null;
        }
        LocalDateTime local = moved.getScheduledAt().atZoneSameInstant(clock.getZone()).toLocalDateTime();
        Optional<Long> block = availabilityDao.findFreeBlock(interviewerId, local.toLocalDate(), local.getHour(),
                moved.getId());
        block.ifPresent(blockId -> availabilityDao.book(blockId, moved.getId()));
        return block.isPresent() ? interviewerId : null;
    }
}
