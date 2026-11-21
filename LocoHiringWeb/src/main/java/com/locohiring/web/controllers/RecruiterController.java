package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.ApplicationsApi;
import com.locohiring.client.api.EventsApi;
import com.locohiring.client.api.FunnelsApi;
import com.locohiring.client.api.InterviewTypesApi;
import com.locohiring.client.api.InterviewersApi;
import com.locohiring.client.api.JobFamiliesApi;
import com.locohiring.client.api.MeApi;
import com.locohiring.client.api.PositionsApi;
import com.locohiring.client.model.ApplicationOverview;
import com.locohiring.client.model.FunnelCreate;
import com.locohiring.client.model.InterviewTypeCreate;
import com.locohiring.client.model.JobFamilyCreate;
import com.locohiring.client.model.PositionCreate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** The recruiter experience: landing page, funnels, catalog, interviewer search, and the admin audit log. */
@Controller
public class RecruiterController {

    private final ApplicationsApi applicationsApi;
    private final MeApi meApi;
    private final FunnelsApi funnelsApi;
    private final InterviewTypesApi interviewTypesApi;
    private final JobFamiliesApi jobFamiliesApi;
    private final PositionsApi positionsApi;
    private final InterviewersApi interviewersApi;
    private final EventsApi eventsApi;

    public RecruiterController(ApplicationsApi applicationsApi, MeApi meApi, FunnelsApi funnelsApi,
                               InterviewTypesApi interviewTypesApi, JobFamiliesApi jobFamiliesApi,
                               PositionsApi positionsApi, InterviewersApi interviewersApi, EventsApi eventsApi) {
        this.applicationsApi = applicationsApi;
        this.meApi = meApi;
        this.funnelsApi = funnelsApi;
        this.interviewTypesApi = interviewTypesApi;
        this.jobFamiliesApi = jobFamiliesApi;
        this.positionsApi = positionsApi;
        this.interviewersApi = interviewersApi;
        this.eventsApi = eventsApi;
    }

    /** Candidates the recruiter owns; the ones that need action first. */
    @GetMapping("/recruiter")
    public String dashboard(Model model) throws ApiException {
        List<ApplicationOverview> overviews = applicationsApi.listApplications().stream()
                .sorted(Comparator.comparing(RecruiterController::needsAction).reversed())
                .toList();
        model.addAttribute("overviews", overviews);
        model.addAttribute("upcoming", meApi.listMyUpcomingInterviews());
        model.addAttribute("debriefs", meApi.listMyUpcomingDebriefs());
        return "recruiter/dashboard";
    }

    static boolean needsAction(ApplicationOverview o) {
        return o.getFeedbackLate() || o.getDebriefLate() || o.getDebriefDue() || o.getReadyToContact()
               || (o.getInterviewsToSchedule() > 0 && "ACTIVE".equals(o.getApplication().getStatus().getValue()));
    }

    @GetMapping("/funnels")
    public String funnels(Model model) throws ApiException {
        model.addAttribute("funnels", funnelsApi.listFunnels());
        model.addAttribute("interviewTypes", interviewTypesApi.listInterviewTypes());
        return "recruiter/funnels";
    }

    /** interviewTypeIds come from several select boxes, in funnel order; empty ones are skipped. */
    @PostMapping("/funnels")
    public String createFunnel(@RequestParam String name, @RequestParam(required = false) String description,
                               @RequestParam(name = "interviewTypeIds", required = false) List<Long> interviewTypeIds,
                               RedirectAttributes redirect) throws ApiException {
        List<Long> steps = interviewTypeIds == null ? List.of() : interviewTypeIds.stream().filter(Objects::nonNull).toList();
        funnelsApi.createFunnel(new FunnelCreate().name(name).description(blankToNull(description)).interviewTypeIds(steps));
        return WebControllerAdvice.done(redirect, "Funnel '" + name + "' created", "/funnels");
    }

    @GetMapping("/catalog")
    public String catalog(Model model) throws ApiException {
        model.addAttribute("jobFamilies", jobFamiliesApi.listJobFamilies());
        model.addAttribute("positions", positionsApi.listPositions());
        model.addAttribute("interviewTypes", interviewTypesApi.listInterviewTypes());
        return "recruiter/catalog";
    }

    @PostMapping("/catalog/job-families")
    public String createJobFamily(@RequestParam String name, RedirectAttributes redirect) throws ApiException {
        jobFamiliesApi.createJobFamily(new JobFamilyCreate().name(name));
        return WebControllerAdvice.done(redirect, "Job family '" + name + "' created", "/catalog");
    }

    @PostMapping("/catalog/positions")
    public String createPosition(@RequestParam String name, @RequestParam(required = false) String description,
                                 @RequestParam int level, @RequestParam long jobFamilyId,
                                 RedirectAttributes redirect) throws ApiException {
        positionsApi.createPosition(new PositionCreate().name(name).description(blankToNull(description))
                .level(level).jobFamilyId(jobFamilyId));
        return WebControllerAdvice.done(redirect, "Position '" + name + "' created", "/catalog");
    }

    @PostMapping("/catalog/interview-types")
    public String createInterviewType(@RequestParam String name, @RequestParam(required = false) String description,
                                      RedirectAttributes redirect) throws ApiException {
        interviewTypesApi.createInterviewType(new InterviewTypeCreate().name(name).description(blankToNull(description)));
        return WebControllerAdvice.done(redirect, "Interview type '" + name + "' created", "/catalog");
    }

    /** Who could interview at a time agreed with a candidate, before creating the interview. */
    @GetMapping("/find-interviewers")
    public String findInterviewers(@RequestParam(required = false) String scheduledAt, Model model)
            throws ApiException {
        if (scheduledAt != null && !scheduledAt.isBlank()) {
            model.addAttribute("scheduledAt", scheduledAt);
            try {
                model.addAttribute("available", interviewersApi.findAvailableInterviewers(Fmt.parse(scheduledAt), null));
            } catch (ApiException e) {
                model.addAttribute("error", WebControllerAdvice.message(e));
            }
        }
        return "recruiter/find-interviewers";
    }

    @GetMapping("/admin/events")
    public String events(Model model) throws ApiException {
        model.addAttribute("events", eventsApi.listEvents(500));
        return "admin/events";
    }

    static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
