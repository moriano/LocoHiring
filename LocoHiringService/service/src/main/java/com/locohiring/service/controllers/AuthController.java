package com.locohiring.service.controllers;

import com.locohiring.api.AuthApi;
import com.locohiring.api.model.LoginRequest;
import com.locohiring.api.model.RefreshRequest;
import com.locohiring.api.model.TokenResponse;
import com.locohiring.service.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthApi {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public ResponseEntity<TokenResponse> login(LoginRequest loginRequest) {
        return ResponseEntity.ok(authService.login(loginRequest.getEmail(), loginRequest.getPassword()));
    }

    @Override
    public ResponseEntity<TokenResponse> refresh(RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refresh(refreshRequest.getRefreshToken()));
    }
}
