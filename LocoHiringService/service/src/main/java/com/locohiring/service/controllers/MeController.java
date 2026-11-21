package com.locohiring.service.controllers;

import com.locohiring.api.MeApi;
import com.locohiring.api.model.CurrentUser;
import com.locohiring.api.model.Debrief;
import com.locohiring.api.model.Interview;
import com.locohiring.api.model.PendingFeedback;
import com.locohiring.service.services.AuthService;
import com.locohiring.service.services.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.locohiring.service.security.Caller.get;

@RestController
public class MeController implements MeApi {

    private final AuthService authService;
    private final DashboardService dashboardService;

    public MeController(AuthService authService, DashboardService dashboardService) {
        this.authService = authService;
        this.dashboardService = dashboardService;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        return ResponseEntity.ok(authService.currentUser(get()));
    }

    @Override
    public ResponseEntity<List<Interview>> listMyUpcomingInterviews() {
        return ResponseEntity.ok(dashboardService.upcomingInterviews(get()));
    }

    @Override
    public ResponseEntity<List<PendingFeedback>> listMyPendingFeedback() {
        return ResponseEntity.ok(dashboardService.pendingFeedback(get()));
    }

    @Override
    public ResponseEntity<List<Debrief>> listMyUpcomingDebriefs() {
        return ResponseEntity.ok(dashboardService.upcomingDebriefs(get()));
    }
}
