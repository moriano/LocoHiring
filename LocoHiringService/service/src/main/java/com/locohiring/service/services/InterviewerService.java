package com.locohiring.service.services;

import com.locohiring.api.model.AvailabilitySlot;
import com.locohiring.api.model.AvailableInterviewer;
import com.locohiring.api.model.Interviewer;
import com.locohiring.api.model.Position;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.ApplicationDao;
import com.locohiring.service.dao.AvailabilityDao;
import com.locohiring.service.dao.InterviewDao;
import com.locohiring.service.dao.InterviewerDao;
import com.locohiring.service.dao.ReferenceDataDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InterviewerService {

    private final InterviewerDao interviewerDao;
    private final AvailabilityDao availabilityDao;
    private final InterviewDao interviewDao;
    private final ApplicationDao applicationDao;
    private final ReferenceDataDao referenceDataDao;
    private final Clock clock;

    public InterviewerService(InterviewerDao interviewerDao, AvailabilityDao availabilityDao,
                              InterviewDao interviewDao, ApplicationDao applicationDao,
                              ReferenceDataDao referenceDataDao, Clock clock) {
        this.interviewerDao = interviewerDao;
        this.availabilityDao = availabilityDao;
        this.interviewDao = interviewDao;
        this.applicationDao = applicationDao;
        this.referenceDataDao = referenceDataDao;
        this.clock = clock;
    }

    public List<Interviewer> list() {
        return interviewerDao.findAll();
    }

    /**
     * Upcoming hours in which at least one interviewer who could lead this candidate's interview is free, so the
     * recruiter can offer those times to the candidate. Uses the same eligibility rule as assigning an interviewer.
     */
    public List<AvailabilitySlot> findEligibleSlots(AuthUser user, long applicationId, LocalDate from, LocalDate to) {
        applicationDao.findVisible(user, applicationId)
                .orElseThrow(() -> ApiException.notFound("Application " + applicationId));
        Position position = referenceDataDao.findPositionOfApplication(applicationId);
        Map<Long, Interviewer> eligible = interviewerDao.findAll().stream()
                .filter(i -> i.getActive() && HiringRules.ineligibilityReason(i, position).isEmpty())
                .collect(Collectors.toMap(Interviewer::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);
        Map<LocalDateTime, AvailabilitySlot> slots = new LinkedHashMap<>();
        for (AvailabilityDao.FreeBlock block : availabilityDao.findFreeBlocksBetween(from, to)) {
            LocalDateTime start = block.date().atTime(block.hour(), 0);
            Interviewer interviewer = eligible.get(block.interviewerId());
            if (interviewer == null || !start.isAfter(now)) {
                continue;
            }
            slots.computeIfAbsent(start, s -> new AvailabilitySlot()
                            .date(block.date()).startHour(block.hour())
                            .scheduledAt(s.atZone(clock.getZone()).toOffsetDateTime())
                            .interviewers(new ArrayList<>()))
                    .getInterviewers().add(interviewer);
        }
        return new ArrayList<>(slots.values());
    }

    /**
     * Interviewers with a free block at the given time. For an interview, eligible main interviewers come first
     * and blocks already booked by that interview count as free. Deactivated interviewers are left out.
     */
    public List<AvailableInterviewer> findAvailable(AuthUser user, OffsetDateTime scheduledAt, Long interviewId) {
        Position position = null;
        if (interviewId != null) {
            long applicationId = interviewDao.findById(interviewId)
                    .orElseThrow(() -> ApiException.notFound("Interview " + interviewId)).getApplicationId();
            applicationDao.findVisible(user, applicationId)
                    .orElseThrow(() -> ApiException.notFound("Interview " + interviewId));
            position = referenceDataDao.findPositionOfInterview(interviewId);
        }
        LocalDateTime local = scheduledAt.atZoneSameInstant(clock.getZone()).toLocalDateTime();
        Map<Long, Interviewer> interviewers = interviewerDao.findAll().stream()
                .collect(Collectors.toMap(Interviewer::getId, Function.identity()));
        Position finalPosition = position;
        return availabilityDao.findFreeBlocks(local.toLocalDate(), local.getHour(), interviewId).stream()
                .filter(row -> interviewers.get(row[0]).getActive())
                .map(row -> {
                    Interviewer interviewer = interviewers.get(row[0]);
                    AvailableInterviewer result = new AvailableInterviewer().interviewer(interviewer).availabilityId(row[1]);
                    if (finalPosition != null) {
                        Optional<String> reason = HiringRules.ineligibilityReason(interviewer, finalPosition);
                        result.eligibleAsMain(reason.isEmpty()).ineligibleReason(reason.orElse(null));
                    }
                    return result;
                })
                .sorted(Comparator.comparing((AvailableInterviewer a) -> !Boolean.TRUE.equals(a.getEligibleAsMain()))
                        .thenComparing(a -> a.getInterviewer().getFirstName()))
                .toList();
    }
}
