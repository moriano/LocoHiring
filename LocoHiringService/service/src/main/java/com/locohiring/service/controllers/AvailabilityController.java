package com.locohiring.service.controllers;

import com.locohiring.api.AvailabilityApi;
import com.locohiring.api.model.AvailabilityBlock;
import com.locohiring.api.model.AvailabilityCreate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.AvailabilityService;
import com.locohiring.service.validators.ScheduleValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@PreAuthorize(Caller.INTERVIEWER)
public class AvailabilityController implements AvailabilityApi {

    private final AvailabilityService availabilityService;
    private final ScheduleValidator scheduleValidator;

    public AvailabilityController(AvailabilityService availabilityService, ScheduleValidator scheduleValidator) {
        this.availabilityService = availabilityService;
        this.scheduleValidator = scheduleValidator;
    }

    @Override
    public ResponseEntity<List<AvailabilityBlock>> listAvailability(LocalDate from, LocalDate to, Long interviewerId) {
        scheduleValidator.validateRange(from, to);
        return ResponseEntity.ok(availabilityService.list(Caller.get(), interviewerId, from, to));
    }

    @Override
    public ResponseEntity<List<AvailabilityBlock>> createAvailability(AvailabilityCreate request) {
        scheduleValidator.validateAvailability(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(availabilityService.create(Caller.get(), request));
    }

    @Override
    public ResponseEntity<Void> deleteAvailability(Long availabilityId) {
        availabilityService.delete(Caller.get(), availabilityId);
        return ResponseEntity.noContent().build();
    }
}
