package com.locohiring.service.controllers;

import com.locohiring.api.FunnelsApi;
import com.locohiring.api.model.Funnel;
import com.locohiring.api.model.FunnelCreate;
import com.locohiring.service.security.Caller;
import com.locohiring.service.services.FunnelService;
import com.locohiring.service.validators.InputValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@PreAuthorize(Caller.RECRUITER)
public class FunnelsController implements FunnelsApi {

    private final FunnelService funnelService;
    private final InputValidator inputValidator;

    public FunnelsController(FunnelService funnelService, InputValidator inputValidator) {
        this.funnelService = funnelService;
        this.inputValidator = inputValidator;
    }

    @Override
    public ResponseEntity<List<Funnel>> listFunnels() {
        return ResponseEntity.ok(funnelService.list());
    }

    @Override
    public ResponseEntity<Funnel> getFunnel(Long funnelId) {
        return ResponseEntity.ok(funnelService.get(funnelId));
    }

    @Override
    public ResponseEntity<Funnel> createFunnel(FunnelCreate request) {
        inputValidator.requireText(request.getName(), "name");
        return ResponseEntity.status(HttpStatus.CREATED).body(funnelService.create(Caller.get(), request));
    }
}
