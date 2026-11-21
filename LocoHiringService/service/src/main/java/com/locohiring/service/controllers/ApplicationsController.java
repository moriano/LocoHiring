package com.locohiring.service.controllers;

import com.locohiring.api.ApplicationsApi;
import com.locohiring.api.model.Application;
import com.locohiring.api.model.ApplicationCreate;
import com.locohiring.api.model.ApplicationDetail;
import com.locohiring.api.model.ApplicationOverview;
import com.locohiring.api.model.ApplicationStatusUpdate;
import com.locohiring.api.model.AvailabilitySlot;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.DebriefCreate;
import com.locohiring.api.model.DebriefUpdate;
import com.locohiring.api.model.Event;
import com.locohiring.api.model.FeedbackList;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewCreate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.ApplicationService;
import com.locohiring.service.services.DebriefService;
import com.locohiring.service.services.FeedbackService;
import com.locohiring.service.services.InterviewerService;
import com.locohiring.service.validators.ScheduleValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class ApplicationsController implements ApplicationsApi {

    private final ApplicationService applicationService;
    private final FeedbackService feedbackService;
    private final DebriefService debriefService;
    private final InterviewerService interviewerService;
    private final ScheduleValidator scheduleValidator;

    public ApplicationsController(ApplicationService applicationService, FeedbackService feedbackService,
                                  DebriefService debriefService, InterviewerService interviewerService,
                                  ScheduleValidator scheduleValidator) {
        this.applicationService = applicationService;
        this.feedbackService = feedbackService;
        this.debriefService = debriefService;
        this.interviewerService = interviewerService;
        this.scheduleValidator = scheduleValidator;
    }

    @Override
    public ResponseEntity<List<ApplicationOverview>> listApplications() {
        return ResponseEntity.ok(applicationService.listOverviews(Caller.get()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Application> createApplication(ApplicationCreate request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(Caller.get(), request));
    }

    @Override
    public ResponseEntity<ApplicationDetail> getApplication(Long applicationId) {
        return ResponseEntity.ok(applicationService.get(Caller.get(), applicationId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Application> updateApplicationStatus(Long applicationId, ApplicationStatusUpdate request) {
        return ResponseEntity.ok(applicationService.updateStatus(Caller.get(), applicationId, request.getStatus()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> createInterview(Long applicationId, InterviewCreate request) {
        scheduleValidator.validateInterviewTime(request.getScheduledAt());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(applicationService.createInterview(Caller.get(), applicationId, request));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<List<AvailabilitySlot>> listEligibleAvailability(Long applicationId, LocalDate from,
                                                                           LocalDate to) {
        scheduleValidator.validateRange(from, to);
        return ResponseEntity.ok(interviewerService.findEligibleSlots(Caller.get(), applicationId, from, to));
    }

    @Override
    public ResponseEntity<FeedbackList> listApplicationFeedback(Long applicationId) {
        return ResponseEntity.ok(feedbackService.list(Caller.get(), applicationId));
    }

    @Override
    public ResponseEntity<List<Event>> listApplicationEvents(Long applicationId) {
        return ResponseEntity.ok(applicationService.listEvents(Caller.get(), applicationId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Debrief> createDebrief(Long applicationId, DebriefCreate request) {
        scheduleValidator.validateFuture(request.getScheduledAt(), "A debrief");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(debriefService.create(Caller.get(), applicationId, request.getScheduledAt()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Debrief> updateDebrief(Long applicationId, DebriefUpdate request) {
        if (request.getScheduledAt() != null) {
            scheduleValidator.validateFuture(request.getScheduledAt(), "A debrief");
        }
        return ResponseEntity.ok(debriefService.update(Caller.get(), applicationId, request));
    }
}
