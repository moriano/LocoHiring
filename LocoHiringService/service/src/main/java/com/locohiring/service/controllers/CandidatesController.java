package com.locohiring.service.controllers;

import com.locohiring.api.CandidatesApi;
import com.locohiring.api.model.Application;
import com.locohiring.api.model.Candidate;
import com.locohiring.api.model.CandidateCreate;
import com.locohiring.api.model.Resume;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.CandidateService;
import com.locohiring.service.validators.InputValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CandidatesController implements CandidatesApi {

    private final CandidateService candidateService;
    private final InputValidator inputValidator;

    public CandidatesController(CandidateService candidateService, InputValidator inputValidator) {
        this.candidateService = candidateService;
        this.inputValidator = inputValidator;
    }

    @Override
    public ResponseEntity<List<Candidate>> listCandidates() {
        return ResponseEntity.ok(candidateService.list(Caller.get()));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Candidate> createCandidate(CandidateCreate request) {
        inputValidator.validateEmail(request.getEmail());
        inputValidator.requireText(request.getFirstName(), "firstName");
        return ResponseEntity.status(HttpStatus.CREATED).body(candidateService.create(Caller.get(), request));
    }

    @Override
    public ResponseEntity<Candidate> getCandidate(Long candidateId) {
        return ResponseEntity.ok(candidateService.get(Caller.get(), candidateId));
    }

    @Override
    public ResponseEntity<List<Application>> listCandidateApplications(Long candidateId) {
        return ResponseEntity.ok(candidateService.listApplications(Caller.get(), candidateId));
    }

    @Override
    public ResponseEntity<Resume> getResume(Long candidateId) {
        return ResponseEntity.ok(candidateService.getResume(Caller.get(), candidateId));
    }

    @Override
    @PreAuthorize(Caller.RECRUITER)
    public ResponseEntity<Void> uploadResume(Long candidateId, Resume resume) {
        inputValidator.validateResume(resume);
        candidateService.uploadResume(Caller.get(), candidateId, resume);
        return ResponseEntity.noContent().build();
    }
}
