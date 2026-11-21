package com.locohiring.service.controllers;

import com.locohiring.api.InterviewersApi;
import com.locohiring.api.model.AvailableInterviewer;
import com.locohiring.api.model.Interviewer;
import com.locohiring.api.model.InterviewerCreate;
import com.locohiring.api.model.InterviewerUpdate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.InterviewerService;
import com.locohiring.service.services.UserService;
import com.locohiring.service.validators.InputValidator;
import com.locohiring.service.validators.ScheduleValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;

@RestController
@PreAuthorize(Caller.RECRUITER)
public class InterviewersController implements InterviewersApi {

    private final InterviewerService interviewerService;
    private final UserService userService;
    private final ScheduleValidator scheduleValidator;
    private final InputValidator inputValidator;

    public InterviewersController(InterviewerService interviewerService, UserService userService,
                                  ScheduleValidator scheduleValidator, InputValidator inputValidator) {
        this.interviewerService = interviewerService;
        this.userService = userService;
        this.scheduleValidator = scheduleValidator;
        this.inputValidator = inputValidator;
    }

    @Override
    @PreAuthorize(Caller.ADMIN)
    public ResponseEntity<Interviewer> createInterviewer(InterviewerCreate request) {
        inputValidator.validateNewUser(request.getEmail(), request.getFirstName(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createInterviewer(Caller.get(), request));
    }

    @Override
    @PreAuthorize(Caller.ADMIN)
    public ResponseEntity<Interviewer> updateInterviewer(Long interviewerId, InterviewerUpdate request) {
        inputValidator.validateUserUpdate(request.getEmail(), request.getFirstName());
        return ResponseEntity.ok(userService.updateInterviewer(Caller.get(), interviewerId, request));
    }

    @Override
    public ResponseEntity<List<Interviewer>> listInterviewers() {
        return ResponseEntity.ok(interviewerService.list());
    }

    @Override
    public ResponseEntity<List<AvailableInterviewer>> findAvailableInterviewers(OffsetDateTime scheduledAt,
                                                                                 Long interviewId) {
        scheduleValidator.validateOnTheHour(scheduledAt);
        return ResponseEntity.ok(interviewerService.findAvailable(Caller.get(), scheduledAt, interviewId));
    }
}
