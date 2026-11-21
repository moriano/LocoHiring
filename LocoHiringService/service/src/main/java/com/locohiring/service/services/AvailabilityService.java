package com.locohiring.service.services;

import com.locohiring.api.model.AvailabilityBlock;
import com.locohiring.api.model.AvailabilityCreate;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.AvailabilityDao;
import com.locohiring.service.dao.EventDao;
import com.locohiring.service.dao.InterviewerDao;
import com.locohiring.service.security.AuthUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Interviewers manage their own one-hour blocks; admins may manage anyone's. */
@Service
public class AvailabilityService {

    private final AvailabilityDao availabilityDao;
    private final InterviewerDao interviewerDao;
    private final EventDao eventDao;

    public AvailabilityService(AvailabilityDao availabilityDao, InterviewerDao interviewerDao, EventDao eventDao) {
        this.availabilityDao = availabilityDao;
        this.interviewerDao = interviewerDao;
        this.eventDao = eventDao;
    }

    public List<AvailabilityBlock> list(AuthUser user, Long interviewerId, LocalDate from, LocalDate to) {
        return availabilityDao.findByInterviewer(targetInterviewer(user, interviewerId), from, to);
    }

    /** Creates a block for every hour from startHour to endHour, every week for the requested number of weeks. */
    @Transactional
    public List<AvailabilityBlock> create(AuthUser user, AvailabilityCreate request) {
        long interviewerId = targetInterviewer(user, request.getInterviewerId());
        int weeks = request.getWeeks() == null ? 1 : request.getWeeks();
        List<AvailabilityBlock> created = new ArrayList<>();
        for (int week = 0; week < weeks; week++) {
            LocalDate date = request.getDate().plusWeeks(week);
            for (int hour = request.getStartHour(); hour < request.getEndHour(); hour++) {
                availabilityDao.createIfAbsent(interviewerId, date, hour).ifPresent(created::add);
            }
        }
        eventDao.record(user.email(), null, null, "Added " + created.size() + " availability blocks for interviewer "
                + interviewerId + ": " + request.getDate() + " " + request.getStartHour() + ":00-"
                + request.getEndHour() + ":00" + (weeks > 1 ? ", weekly for " + weeks + " weeks" : ""));
        return created;
    }

    @Transactional
    public void delete(AuthUser user, long blockId) {
        AvailabilityBlock block = availabilityDao.findById(blockId)
                .filter(b -> user.admin() || (user.isInterviewer() && b.getInterviewerId() == user.id()))
                .orElseThrow(() -> ApiException.notFound("Availability block " + blockId));
        if (block.getInterviewId() != null) {
            throw ApiException.conflict("The block is booked for interview " + block.getInterviewId());
        }
        availabilityDao.delete(blockId);
        eventDao.record(user.email(), null, null, "Removed availability block of interviewer "
                + block.getInterviewerId() + " on " + block.getDate() + " at " + block.getStartHour() + ":00");
    }

    private long targetInterviewer(AuthUser user, Long requested) {
        if (requested == null) {
            if (!user.isInterviewer()) {
                throw ApiException.badRequest("interviewerId is required");
            }
            return user.id();
        }
        if (user.isInterviewer() && requested == user.id()) {
            return requested;
        }
        if (!user.admin()) {
            throw ApiException.forbidden("Interviewers can only manage their own availability");
        }
        interviewerDao.findById(requested)
                .orElseThrow(() -> ApiException.badRequest("Interviewer " + requested + " does not exist"));
        return requested;
    }
}
