package com.locohiring.service.controllers;

import com.locohiring.api.InterviewsApi;
import com.locohiring.api.model.Feedback;
import com.locohiring.api.model.FeedbackCreate;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.InterviewAssignment;
import com.locohiring.api.model.InterviewSchedule;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.FeedbackService;
import com.locohiring.service.services.InterviewService;
import com.locohiring.service.validators.InputValidator;
import com.locohiring.service.validators.ScheduleValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InterviewsController implements InterviewsApi {

    private final InterviewService interviewService;
    private final FeedbackService feedbackService;
    private final ScheduleValidator scheduleValidator;
    private final InputValidator inputValidator;

    public InterviewsController(InterviewService interviewService, FeedbackService feedbackService,
                                ScheduleValidator scheduleValidator, InputValidator inputValidator) {
        this.interviewService = interviewService;
        this.feedbackService = feedbackService;
        this.scheduleValidator = scheduleValidator;
        this.inputValidator = inputValidator;
    }

    @Override
    public ResponseEntity<Interview> getInterview(Long interviewId) {
        return ResponseEntity.ok(interviewService.get(Caller.get(), interviewId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> rescheduleInterview(Long interviewId, InterviewSchedule request) {
        scheduleValidator.validateInterviewTime(request.getScheduledAt());
        return ResponseEntity.ok(interviewService.reschedule(Caller.get(), interviewId, request.getScheduledAt()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> assignInterviewers(Long interviewId, InterviewAssignment request) {
        return ResponseEntity.ok(interviewService.assign(Caller.get(), interviewId, request.getInterviewerId(),
                request.getShadowId()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> unassignInterviewers(Long interviewId) {
        return ResponseEntity.ok(interviewService.unassign(Caller.get(), interviewId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> cancelInterview(Long interviewId) {
        return ResponseEntity.ok(interviewService.cancel(Caller.get(), interviewId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Interview> completeInterview(Long interviewId) {
        return ResponseEntity.ok(interviewService.complete(Caller.get(), interviewId));
    }

    @Override
    @PreAuthorize(Caller.INTERVIEWER)
    public ResponseEntity<Feedback> submitFeedback(Long interviewId, FeedbackCreate request) {
        inputValidator.requireText(request.getFeedback(), "feedback");
        return ResponseEntity.status(HttpStatus.CREATED).body(feedbackService.submit(Caller.get(), interviewId,
                request.getFeedback().trim(), request.getInclined()));
    }
}
