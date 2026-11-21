package com.locohiring.service.services;

import com.locohiring.api.model.Application;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.PendingFeedback;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.DebriefDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/** Data for the landing pages of the logged-in user. */
@Service
public class DashboardService {

    private final InterviewDao interviewDao;
    private final DebriefDao debriefDao;
    private final ApplicationDao applicationDao;
    private final Clock clock;

    public DashboardService(InterviewDao interviewDao, DebriefDao debriefDao, ApplicationDao applicationDao,
                            Clock clock) {
        this.interviewDao = interviewDao;
        this.debriefDao = debriefDao;
        this.applicationDao = applicationDao;
        this.clock = clock;
    }

    public List<Interview> upcomingInterviews(AuthUser user) {
        return interviewDao.findUpcoming(user, OffsetDateTime.now(clock));
    }

    public List<PendingFeedback> pendingFeedback(AuthUser user) {
        if (!user.isInterviewer()) {
            return List.of();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        return interviewDao.findEndedWithoutFeedback(user.id(), now).stream()
                .map(i -> new PendingFeedback()
                        .interview(i)
                        .dueAt(HiringRules.feedbackDueAt(i))
                        .late(now.isAfter(HiringRules.feedbackDueAt(i))))
                .toList();
    }

    public List<Debrief> upcomingDebriefs(AuthUser user) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Debrief> debriefs = debriefDao.findUpcoming(user, now);
        List<Long> ids = debriefs.stream().map(Debrief::getApplicationId).toList();
        Map<Long, Integer> funnelSizes = applicationDao.funnelSizes(ids);
        List<Interview> interviews = interviewDao.findByApplicationIds(ids);
        return debriefs.stream().map(d -> {
            Application application = applicationDao.findById(d.getApplicationId()).orElseThrow();
            return HiringRules.withDeadline(d, application.getStatus(), funnelSizes.getOrDefault(d.getApplicationId(), 0),
                    HiringRules.ofApplication(interviews, d.getApplicationId()), now);
        }).toList();
    }
}
