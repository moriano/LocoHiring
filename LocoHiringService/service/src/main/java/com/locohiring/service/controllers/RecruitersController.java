package com.locohiring.service.controllers;

import com.locohiring.api.RecruitersApi;
import com.locohiring.api.model.Recruiter;
import com.locohiring.api.model.RecruiterCreate;
import com.locohiring.api.model.RecruiterUpdate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.UserService;
import com.locohiring.service.validators.InputValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize(Caller.ADMIN)
public class RecruitersController implements RecruitersApi {

    private final UserService userService;
    private final InputValidator inputValidator;

    public RecruitersController(UserService userService, InputValidator inputValidator) {
        this.userService = userService;
        this.inputValidator = inputValidator;
    }

    @Override
    public ResponseEntity<List<Recruiter>> listRecruiters() {
        return ResponseEntity.ok(userService.listRecruiters());
    }

    @Override
    public ResponseEntity<Recruiter> createRecruiter(RecruiterCreate request) {
        inputValidator.validateNewUser(request.getEmail(), request.getFirstName(), request.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createRecruiter(Caller.get(), request));
    }

    @Override
    public ResponseEntity<Recruiter> updateRecruiter(Long recruiterId, RecruiterUpdate request) {
        inputValidator.validateUserUpdate(request.getEmail(), request.getFirstName());
        return ResponseEntity.ok(userService.updateRecruiter(Caller.get(), recruiterId, request));
    }
}
