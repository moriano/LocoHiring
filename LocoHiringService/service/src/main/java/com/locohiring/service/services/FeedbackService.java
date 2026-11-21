package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.Feedback;
import com.locohiring.api.model.FeedbackList;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewStatus;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.FeedbackDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackDao feedbackDao;
    private final InterviewDao interviewDao;
    private final ApplicationDao applicationDao;
    private final EventDao eventDao;
    private final Clock clock;

    public FeedbackService(FeedbackDao feedbackDao, InterviewDao interviewDao, ApplicationDao applicationDao,
                           EventDao eventDao, Clock clock) {
        this.feedbackDao = feedbackDao;
        this.interviewDao = interviewDao;
        this.applicationDao = applicationDao;
        this.eventDao = eventDao;
        this.clock = clock;
    }

    @Transactional
    public Feedback submit(AuthUser user, long interviewId, String text, boolean inclined) {
        Interview interview = interviewDao.findById(interviewId)
                .orElseThrow(() -> ApiException.notFound("Interview " + interviewId));
        if (!user.isInterviewer() || !HiringRules.isParticipant(interview, user.id())) {
            throw ApiException.forbidden("Only the interviewers of this interview can write its feedback");
        }
        if (interview.getStatus() == InterviewStatus.CANCELLED) {
            throw ApiException.badRequest("The interview was cancelled");
        }
        if (interview.getScheduledAt().isAfter(OffsetDateTime.now(clock))) {
            throw ApiException.badRequest("The interview has not started yet");
        }
        if (feedbackDao.exists(interviewId, user.id())) {
            throw ApiException.conflict("You already wrote feedback for this interview");
        }
        Feedback feedback = feedbackDao.create(interviewId, user.id(), text, inclined);
        interviewDao.updateStatus(interviewId, InterviewStatus.COMPLETED);
        Application application = applicationDao.findById(interview.getApplicationId()).orElseThrow();
        eventDao.record(user.email(), application.getFunnelId(), application.getId(),
                feedback.getInterviewerName() + " submitted feedback for interview " + interviewId
                        + " (" + (inclined ? "inclined" : "not inclined") + ")");
        return feedback;
    }

    /**
     * Interviewers see the other interviewers' feedback only once they have written theirs for every interview of
     * this application they take part in, so that they are not biased by it.
     */
    public FeedbackList list(AuthUser user, long applicationId) {
        Application application = applicationDao.findVisible(user, applicationId)
                .orElseThrow(() -> ApiException.notFound("Application " + applicationId));
        List<Feedback> all = feedbackDao.findByApplicationIds(List.of(applicationId));
        boolean seesAll = user.admin() || (user.isRecruiter() && application.getRecruiterId() == user.id());
        if (!seesAll && user.isInterviewer()) {
            List<Interview> mine = interviewDao.findByApplicationIds(List.of(applicationId)).stream()
                    .filter(i -> i.getStatus() != InterviewStatus.CANCELLED && HiringRules.isParticipant(i, user.id()))
                    .toList();
            seesAll = !mine.isEmpty() && mine.stream().allMatch(i -> all.stream().anyMatch(
                    f -> f.getInterviewId().equals(i.getId()) && f.getInterviewerId() == user.id()));
        }
        List<Feedback> visible = seesAll ? all
                : all.stream().filter(f -> user.isInterviewer() && f.getInterviewerId() == user.id()).toList();
        return new FeedbackList().feedback(visible).hiddenCount(all.size() - visible.size());
    }
}
