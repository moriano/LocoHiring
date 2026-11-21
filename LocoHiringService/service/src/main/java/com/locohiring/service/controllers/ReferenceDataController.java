package com.locohiring.service.controllers;

import com.locohiring.api.InterviewTypesApi;
import com.locohiring.api.JobFamiliesApi;
import com.locohiring.api.PositionsApi;
import com.locohiring.api.model.InterviewType;
import com.locohiring.api.model.InterviewTypeCreate;
import com.locohiring.api.model.JobFamily;
import com.locohiring.api.model.JobFamilyCreate;
import com.locohiring.api.model.Position;
import com.locohiring.api.model.PositionCreate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.ReferenceDataService;
import com.locohiring.service.validators.InputValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Job families, positions and interview types: readable by everyone, created by recruiters. */
@RestController
public class ReferenceDataController implements JobFamiliesApi, PositionsApi, InterviewTypesApi {

    private final ReferenceDataService service;
    private final InputValidator inputValidator;

    public ReferenceDataController(ReferenceDataService service, InputValidator inputValidator) {
        this.service = service;
        this.inputValidator = inputValidator;
    }

    @Override
    public ResponseEntity<List<JobFamily>> listJobFamilies() {
        return ResponseEntity.ok(service.listJobFamilies());
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<JobFamily> createJobFamily(JobFamilyCreate request) {
        inputValidator.requireText(request.getName(), "name");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createJobFamily(Caller.get(), request.getName().trim()));
    }

    @Override
    public ResponseEntity<List<Position>> listPositions() {
        return ResponseEntity.ok(service.listPositions());
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Position> createPosition(PositionCreate request) {
        inputValidator.requireText(request.getName(), "name");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createPosition(Caller.get(), request));
    }

    @Override
    public ResponseEntity<List<InterviewType>> listInterviewTypes() {
        return ResponseEntity.ok(service.listInterviewTypes());
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<InterviewType> createInterviewType(InterviewTypeCreate request) {
        inputValidator.requireText(request.getName(), "name");
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createInterviewType(Caller.get(), request));
    }
}
