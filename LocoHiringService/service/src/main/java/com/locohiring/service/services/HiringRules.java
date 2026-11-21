package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.ApplicationOverview;
import com.locohiring.api.model.ApplicationStatus;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.Feedback;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewStatus;
import com.locohiring.api.model.Interviewer;
import com.locohiring.api.model.Position;
import com.locohiring.service.ApiException;
import com.locohiring.service.security.AuthUser;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/** The hiring rules that do not need the database: deadlines, flags and ownership. */
final class HiringRules {

    static final Duration INTERVIEW_LENGTH = Duration.ofHours(1);
    static final Duration FEEDBACK_WINDOW = Duration.ofHours(24);
    static final Duration DEBRIEF_WINDOW = Duration.ofDays(7);

    private static final DateTimeFormatter EVENT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private HiringRules() {
    }

    /** A time as written in event messages, in the service's zone. */
    static String time(OffsetDateTime time) {
        return time.atZoneSameInstant(ZoneId.systemDefault()).format(EVENT_TIME);
    }

    /** Only the recruiter who owns the application, or an admin, may change it. */
    static void requireOwner(AuthUser user, Application application) {
        if (!user.admin() && !(user.isRecruiter() && application.getRecruiterId() == user.id())) {
            throw ApiException.forbidden("Only the recruiter of this application can change it");
        }
    }

    static OffsetDateTime end(Interview interview) {
        return interview.getScheduledAt().plus(INTERVIEW_LENGTH);
    }

    static OffsetDateTime feedbackDueAt(Interview interview) {
        return end(interview).plus(FEEDBACK_WINDOW);
    }

    static boolean hasEnded(Interview interview, OffsetDateTime now) {
        return !end(interview).isAfter(now);
    }

    static boolean isParticipant(Interview interview, long interviewerId) {
        return Objects.equals(interview.getInterviewerId(), interviewerId)
                || Objects.equals(interview.getShadowId(), interviewerId);
    }

    static List<Long> participants(Interview interview) {
        return Stream.of(interview.getInterviewerId(), interview.getShadowId()).filter(Objects::nonNull).toList();
    }

    private static List<Interview> notCancelled(List<Interview> interviews) {
        return interviews.stream().filter(i -> i.getStatus() != InterviewStatus.CANCELLED).toList();
    }

    /**
     * When every interview of the funnel has taken place, the debrief is due 7 days after the last one ends.
     */
    static Optional<OffsetDateTime> debriefDueAt(int funnelSize, List<Interview> interviews, OffsetDateTime now) {
        List<Interview> held = notCancelled(interviews);
        boolean allTookPlace = funnelSize > 0 && held.size() >= funnelSize
                && held.stream().allMatch(i -> i.getInterviewerId() != null && hasEnded(i, now));
        if (!allTookPlace) {
            return Optional.empty();
        }
        return held.stream().map(HiringRules::end).max(Comparator.naturalOrder()).map(t -> t.plus(DEBRIEF_WINDOW));
    }

    /** Fills in the debrief's dueAt and late fields. */
    static Debrief withDeadline(Debrief debrief, ApplicationStatus status, int funnelSize, List<Interview> interviews,
                                OffsetDateTime now) {
        Optional<OffsetDateTime> dueAt = debriefDueAt(funnelSize, interviews, now);
        debrief.setDueAt(dueAt.orElse(null));
        debrief.setLate(isDebriefLate(debrief, status, dueAt, now));
        return debrief;
    }

    private static boolean isDebriefLate(Debrief debrief, ApplicationStatus status, Optional<OffsetDateTime> dueAt,
                                         OffsetDateTime now) {
        boolean completed = debrief != null && debrief.getCompleted();
        return !completed && status == ApplicationStatus.ACTIVE && dueAt.map(now::isAfter).orElse(false);
    }

    /** The flags that tell the recruiter what to do next with an application. */
    static ApplicationOverview overview(Application application, int funnelSize, List<Interview> interviews,
                                        List<Feedback> feedback, Debrief debrief, OffsetDateTime now) {
        List<Interview> held = notCancelled(interviews);
        int pending = 0;
        boolean feedbackLate = false;
        for (Interview interview : held) {
            if (interview.getStatus() == InterviewStatus.LOOKING_FOR_INTERVIEWER || !hasEnded(interview, now)) {
                continue;
            }
            for (Long interviewerId : participants(interview)) {
                boolean written = feedback.stream().anyMatch(f -> f.getInterviewId().equals(interview.getId())
                        && f.getInterviewerId().equals(interviewerId));
                if (!written) {
                    pending++;
                    feedbackLate |= now.isAfter(feedbackDueAt(interview));
                }
            }
        }
        long completed = held.stream().filter(i -> i.getStatus() == InterviewStatus.COMPLETED).count();
        long looking = held.stream().filter(i -> i.getStatus() == InterviewStatus.LOOKING_FOR_INTERVIEWER).count();
        boolean active = application.getStatus() == ApplicationStatus.ACTIVE;
        boolean debriefCompleted = debrief != null && debrief.getCompleted();
        Optional<OffsetDateTime> dueAt = debriefDueAt(funnelSize, interviews, now);
        if (debrief != null) {
            withDeadline(debrief, application.getStatus(), funnelSize, interviews, now);
        }
        return new ApplicationOverview()
                .application(application)
                .totalInterviews(funnelSize)
                .completedInterviews((int) completed)
                .interviewsToSchedule((int) (Math.max(0, funnelSize - held.size()) + looking))
                .pendingFeedback(pending)
                .feedbackLate(feedbackLate)
                .debrief(debrief)
                .debriefDue(active && !debriefCompleted && dueAt.isPresent() && pending == 0)
                .debriefLate(isDebriefLate(debrief, application.getStatus(), dueAt, now))
                .readyToContact(active && debriefCompleted);
    }

    /** Why the interviewer cannot be the main interviewer for this position, if they cannot. */
    static Optional<String> ineligibilityReason(Interviewer interviewer, Position position) {
        if (interviewer.getLevel() < position.getLevel()) {
            return Optional.of(name(interviewer) + " is level " + interviewer.getLevel()
                    + " and cannot interview for a level " + position.getLevel() + " position");
        }
        if (interviewer.getJobFamilies().stream().noneMatch(f -> f.getId().equals(position.getJobFamilyId()))) {
            return Optional.of(name(interviewer) + " does not interview for " + position.getJobFamilyName());
        }
        return Optional.empty();
    }

    static String name(Interviewer interviewer) {
        return interviewer.getSurname() == null ? interviewer.getFirstName()
                : interviewer.getFirstName() + " " + interviewer.getSurname();
    }

    /** Interviews of one application. */
    static List<Interview> ofApplication(List<Interview> interviews, long applicationId) {
        return new ArrayList<>(interviews.stream().filter(i -> i.getApplicationId() == applicationId).toList());
    }
}
