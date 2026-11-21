package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.ApplicationsApi;
import com.locohiring.client.api.InterviewsApi;
import com.locohiring.client.model.ApplicationDetail;
import com.locohiring.client.model.ApplicationStatus;
import com.locohiring.client.model.ApplicationStatusUpdate;
import com.locohiring.client.model.AvailabilitySlot;
import com.locohiring.client.model.DebriefCreate;
import com.locohiring.client.model.DebriefUpdate;
import com.locohiring.client.model.Interview;
import com.locohiring.client.model.InterviewAssignment;
import com.locohiring.client.model.InterviewCreate;
import com.locohiring.client.model.InterviewStatus;
import com.locohiring.client.model.InterviewType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** One candidate going through one funnel: interviews, feedback, debrief, outcome and history. */
@Controller
public class ApplicationController {

    private final ApplicationsApi applicationsApi;
    private final InterviewsApi interviewsApi;
    private final Fmt fmt;

    public ApplicationController(ApplicationsApi applicationsApi, InterviewsApi interviewsApi, Fmt fmt) {
        this.applicationsApi = applicationsApi;
        this.interviewsApi = interviewsApi;
        this.fmt = fmt;
    }

    /** A funnel step and the interview created for it, if any. */
    public record Step(InterviewType type, Interview interview) {
    }

    @GetMapping("/applications/{id}")
    public String detail(@PathVariable long id, Model model) throws ApiException {
        ApplicationDetail detail = applicationsApi.getApplication(id);
        model.addAttribute("detail", detail);
        model.addAttribute("steps", steps(detail));
        model.addAttribute("feedback", applicationsApi.listApplicationFeedback(id));
        model.addAttribute("events", applicationsApi.listApplicationEvents(id));
        model.addAttribute("statuses", ApplicationStatus.values());
        return "applications/detail";
    }

    /**
     * Matches each funnel step with an interview of that type (cancelled interviews only when nothing else
     * matches), and lists extra interviews of the same type as additional steps.
     */
    static List<Step> steps(ApplicationDetail detail) {
        List<Interview> remaining = new ArrayList<>(detail.getInterviews());
        remaining.sort((a, b) -> Boolean.compare(a.getStatus() == InterviewStatus.CANCELLED,
                b.getStatus() == InterviewStatus.CANCELLED));
        List<Step> steps = new ArrayList<>();
        for (InterviewType type : detail.getFunnel().getInterviewTypes()) {
            Interview match = remaining.stream().filter(i -> i.getInterviewTypeId().equals(type.getId())).findFirst()
                    .orElse(null);
            remaining.remove(match);
            steps.add(new Step(type, match));
        }
        for (Interview extra : remaining) {
            steps.add(new Step(new InterviewType().id(extra.getInterviewTypeId()).name(extra.getInterviewTypeName()), extra));
        }
        return steps;
    }

    private static final int FIND_TIME_DAYS = 14;

    /**
     * Availability first: free hours of interviewers who could lead this candidate's interview, to offer the
     * candidate or to book straight away.
     */
    @GetMapping("/applications/{id}/find-time")
    public String findTime(@PathVariable long id, @RequestParam(defaultValue = "0") int page, Model model)
            throws ApiException {
        ApplicationDetail detail = applicationsApi.getApplication(id);
        LocalDate from = LocalDate.now().plusDays((long) Math.max(page, 0) * FIND_TIME_DAYS);
        LocalDate to = from.plusDays(FIND_TIME_DAYS - 1);
        List<AvailabilitySlot> slots = applicationsApi.listEligibleAvailability(id, from, to);
        List<Step> steps = steps(detail);
        model.addAttribute("detail", detail);
        model.addAttribute("steps", steps);
        model.addAttribute("nextType", steps.stream().filter(s -> s.interview() == null
                        || s.interview().getStatus() == InterviewStatus.CANCELLED)
                .map(s -> s.type().getId()).findFirst().orElse(null));
        model.addAttribute("byDay", slots.stream().collect(Collectors.groupingBy(AvailabilitySlot::getDate,
                TreeMap::new, Collectors.toList())));
        model.addAttribute("slotCount", slots.size());
        model.addAttribute("offerText", slots.isEmpty() ? "No times available."
                : slots.stream().map(slot -> "• " + fmt.slot(slot.getScheduledAt())).collect(Collectors.joining("\n")));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("page", Math.max(page, 0));
        return "applications/find-time";
    }

    /** Creates the interview at the chosen slot and assigns the chosen interviewer. slot is "start|interviewerId". */
    @PostMapping("/applications/{id}/book")
    public String book(@PathVariable long id, @RequestParam long interviewTypeId, @RequestParam String slot,
                       RedirectAttributes redirect) throws ApiException {
        String[] parts = slot.split("\\|");
        OffsetDateTime scheduledAt = OffsetDateTime.parse(parts[0]);
        long interviewerId = Long.parseLong(parts[1]);
        Interview interview = applicationsApi.createInterview(id,
                new InterviewCreate().interviewTypeId(interviewTypeId).scheduledAt(scheduledAt));
        try {
            interview = interviewsApi.assignInterviewers(interview.getId(),
                    new InterviewAssignment().interviewerId(interviewerId));
        } catch (ApiException e) {
            redirect.addFlashAttribute("error", "The interview was created, but the interviewer could not be booked: "
                    + WebControllerAdvice.message(e) + ". Pick someone else below.");
            return "redirect:/interviews/" + interview.getId();
        }
        return WebControllerAdvice.done(redirect, interview.getInterviewTypeName() + " booked with "
                + interview.getInterviewerName(), "/interviews/" + interview.getId());
    }

    @PostMapping("/applications/{id}/interviews")
    public String createInterview(@PathVariable long id, @RequestParam long interviewTypeId,
                                  @RequestParam String scheduledAt, RedirectAttributes redirect) throws ApiException {
        Interview interview = applicationsApi.createInterview(id, new InterviewCreate()
                .interviewTypeId(interviewTypeId).scheduledAt(Fmt.parse(scheduledAt)));
        return WebControllerAdvice.done(redirect, "Interview created; now find an interviewer",
                "/interviews/" + interview.getId());
    }

    @PostMapping("/applications/{id}/status")
    public String updateStatus(@PathVariable long id, @RequestParam ApplicationStatus status,
                               RedirectAttributes redirect) throws ApiException {
        applicationsApi.updateApplicationStatus(id, new ApplicationStatusUpdate().status(status));
        return WebControllerAdvice.done(redirect, "Status set to " + status, "/applications/" + id);
    }

    @PostMapping("/applications/{id}/debrief")
    public String scheduleDebrief(@PathVariable long id, @RequestParam String scheduledAt, RedirectAttributes redirect)
            throws ApiException {
        applicationsApi.createDebrief(id, new DebriefCreate().scheduledAt(Fmt.parse(scheduledAt)));
        return WebControllerAdvice.done(redirect, "Debrief scheduled", "/applications/" + id);
    }

    @PostMapping("/applications/{id}/debrief/reschedule")
    public String rescheduleDebrief(@PathVariable long id, @RequestParam String scheduledAt,
                                    RedirectAttributes redirect) throws ApiException {
        applicationsApi.updateDebrief(id, new DebriefUpdate().scheduledAt(Fmt.parse(scheduledAt)));
        return WebControllerAdvice.done(redirect, "Debrief rescheduled", "/applications/" + id);
    }

    @PostMapping("/applications/{id}/debrief/outcome")
    public String recordDebrief(@PathVariable long id, @RequestParam String notes, @RequestParam boolean inclined,
                                RedirectAttributes redirect) throws ApiException {
        applicationsApi.updateDebrief(id, new DebriefUpdate().notes(notes).inclined(inclined));
        return WebControllerAdvice.done(redirect, "Debrief outcome recorded", "/applications/" + id);
    }
}
