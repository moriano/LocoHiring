package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.ApplicationCreate;
import com.locohiring.api.model.ApplicationDetail;
import com.locohiring.api.model.ApplicationOverview;
import com.locohiring.api.model.ApplicationStatus;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.Event;
import com.locohiring.api.model.Feedback;
import com.locohiring.api.model.Funnel;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewCreate;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.CandidateDao;
import com.locohiring.service.dao.DebriefDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.FeedbackDao;
import com.locohiring.service.dao.FunnelDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.dao.UserDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationDao applicationDao;
    private final CandidateDao candidateDao;
    private final FunnelDao funnelDao;
    private final InterviewDao interviewDao;
    private final FeedbackDao feedbackDao;
    private final DebriefDao debriefDao;
    private final UserDao userDao;
    private final EventDao eventDao;
    private final Clock clock;

    public ApplicationService(ApplicationDao applicationDao, CandidateDao candidateDao, FunnelDao funnelDao,
                              InterviewDao interviewDao, FeedbackDao feedbackDao, DebriefDao debriefDao,
                              UserDao userDao, EventDao eventDao, Clock clock) {
        this.applicationDao = applicationDao;
        this.candidateDao = candidateDao;
        this.funnelDao = funnelDao;
        this.interviewDao = interviewDao;
        this.feedbackDao = feedbackDao;
        this.debriefDao = debriefDao;
        this.userDao = userDao;
        this.eventDao = eventDao;
        this.clock = clock;
    }

    public List<ApplicationOverview> listOverviews(AuthUser user) {
        List<Application> applications = applicationDao.findVisible(user);
        List<Long> ids = applications.stream().map(Application::getId).toList();
        Map<Long, Integer> funnelSizes = applicationDao.funnelSizes(ids);
        List<Interview> interviews = interviewDao.findByApplicationIds(ids);
        List<Feedback> feedback = feedbackDao.findByApplicationIds(ids);
        Map<Long, Debrief> debriefs = debriefDao.findByApplicationIds(ids).stream()
                .collect(Collectors.toMap(Debrief::getApplicationId, Function.identity()));
        OffsetDateTime now = OffsetDateTime.now(clock);
        return applications.stream().map(a -> {
            List<Interview> own = HiringRules.ofApplication(interviews, a.getId());
            List<Long> interviewIds = own.stream().map(Interview::getId).toList();
            List<Feedback> ownFeedback = feedback.stream().filter(f -> interviewIds.contains(f.getInterviewId())).toList();
            return HiringRules.overview(a, funnelSizes.getOrDefault(a.getId(), 0), own, ownFeedback,
                    debriefs.get(a.getId()), now);
        }).toList();
    }

    public ApplicationDetail get(AuthUser user, long id) {
        Application application = find(user, id);
        Funnel funnel = funnelDao.findById(application.getFunnelId()).orElseThrow();
        List<Interview> interviews = interviewDao.findByApplicationIds(List.of(id));
        Debrief debrief = debriefDao.findByApplication(id)
                .map(d -> HiringRules.withDeadline(d, application.getStatus(), funnel.getInterviewTypes().size(),
                        interviews, OffsetDateTime.now(clock)))
                .orElse(null);
        return new ApplicationDetail()
                .application(application)
                .candidate(candidateDao.findById(application.getCandidateId()).orElseThrow())
                .funnel(funnel)
                .interviews(interviews)
                .debrief(debrief);
    }

    @Transactional
    public Application create(AuthUser user, ApplicationCreate request) {
        long recruiterId;
        if (request.getRecruiterId() != null && request.getRecruiterId() != user.id() && !user.admin()) {
            throw ApiException.forbidden("Recruiters can only create applications they own");
        } else if (request.getRecruiterId() != null) {
            recruiterId = request.getRecruiterId();
        } else if (user.isRecruiter()) {
            recruiterId = user.id();
        } else {
            throw ApiException.badRequest("recruiterId is required");
        }
        if (!userDao.recruiterExists(recruiterId)) {
            throw ApiException.badRequest("Recruiter " + recruiterId + " does not exist");
        }
        candidateDao.findVisible(user, request.getCandidateId())
                .orElseThrow(() -> ApiException.notFound("Candidate " + request.getCandidateId()));
        Funnel funnel = funnelDao.findById(request.getFunnelId())
                .orElseThrow(() -> ApiException.notFound("Funnel " + request.getFunnelId()));
        if (applicationDao.activeExists(request.getCandidateId(), request.getFunnelId())) {
            throw ApiException.conflict("The candidate already has an active application in this funnel");
        }
        long id = applicationDao.create(request.getCandidateId(), request.getFunnelId(), recruiterId);
        Application application = applicationDao.findById(id).orElseThrow();
        eventDao.record(user.email(), funnel.getId(), id, "Candidate " + application.getCandidateName()
                + " entered funnel '" + funnel.getName() + "', owned by " + application.getRecruiterName());
        return application;
    }

    @Transactional
    public Application updateStatus(AuthUser user, long id, ApplicationStatus status) {
        Application application = find(user, id);
        HiringRules.requireOwner(user, application);
        applicationDao.updateStatus(id, status);
        eventDao.record(user.email(), application.getFunnelId(), id,
                "Application status changed from " + application.getStatus() + " to " + status);
        return applicationDao.findById(id).orElseThrow();
    }

    @Transactional
    public Interview createInterview(AuthUser user, long applicationId, InterviewCreate request) {
        Application application = find(user, applicationId);
        HiringRules.requireOwner(user, application);
        if (application.getStatus() != ApplicationStatus.ACTIVE) {
            throw ApiException.badRequest("The application is " + application.getStatus());
        }
        if (!applicationDao.funnelHasInterviewType(applicationId, request.getInterviewTypeId())) {
            throw ApiException.badRequest("Interview type " + request.getInterviewTypeId()
                    + " is not part of funnel '" + application.getFunnelName() + "'");
        }
        long id = interviewDao.create(applicationId, request.getInterviewTypeId(), request.getScheduledAt());
        Interview interview = interviewDao.findById(id).orElseThrow();
        eventDao.record(user.email(), application.getFunnelId(), applicationId,
                "Created " + interview.getInterviewTypeName() + " interview " + id + " at " + HiringRules.time(interview.getScheduledAt()));
        return interview;
    }

    public List<Event> listEvents(AuthUser user, long applicationId) {
        find(user, applicationId);
        return eventDao.findByApplication(applicationId);
    }

    private Application find(AuthUser user, long id) {
        return applicationDao.findVisible(user, id).orElseThrow(() -> ApiException.notFound("Application " + id));
    }
}
