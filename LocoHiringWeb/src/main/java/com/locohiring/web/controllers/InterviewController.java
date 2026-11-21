package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.ApplicationsApi;
import com.locohiring.client.api.InterviewersApi;
import com.locohiring.client.api.InterviewsApi;
import com.locohiring.client.model.FeedbackCreate;
import com.locohiring.client.model.FeedbackList;
import com.locohiring.client.model.Interview;
import com.locohiring.client.model.InterviewAssignment;
import com.locohiring.client.model.InterviewSchedule;
import com.locohiring.client.model.InterviewStatus;
import com.locohiring.web.auth.WebUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.OffsetDateTime;
import java.util.Objects;

@Controller
public class InterviewController {

    private final InterviewsApi interviewsApi;
    private final ApplicationsApi applicationsApi;
    private final InterviewersApi interviewersApi;

    public InterviewController(InterviewsApi interviewsApi, ApplicationsApi applicationsApi,
                               InterviewersApi interviewersApi) {
        this.interviewsApi = interviewsApi;
        this.applicationsApi = applicationsApi;
        this.interviewersApi = interviewersApi;
    }

    @GetMapping("/interviews/{id}")
    public String detail(@PathVariable long id, @ModelAttribute("me") WebUser me, Model model) throws ApiException {
        Interview interview = interviewsApi.getInterview(id);
        FeedbackList feedback = applicationsApi.listApplicationFeedback(interview.getApplicationId());
        boolean participant = me.interviewer() && (Objects.equals(interview.getInterviewerId(), me.id())
                                                   || Objects.equals(interview.getShadowId(), me.id()));
        boolean started = !interview.getScheduledAt().isAfter(OffsetDateTime.now());
        boolean wroteFeedback = feedback.getFeedback().stream()
                .anyMatch(f -> f.getInterviewId().equals(id) && f.getInterviewerId().equals(me.id()) && me.interviewer());
        boolean open = interview.getStatus() == InterviewStatus.LOOKING_FOR_INTERVIEWER
                       || interview.getStatus() == InterviewStatus.SCHEDULED;

        model.addAttribute("interview", interview);
        model.addAttribute("detail", applicationsApi.getApplication(interview.getApplicationId()));
        model.addAttribute("feedback", feedback);
        model.addAttribute("canWriteFeedback", participant && started && !wroteFeedback
                                               && interview.getStatus() != InterviewStatus.CANCELLED);
        model.addAttribute("canSchedule", me.seesRecruiterPages() && open && !started);
        model.addAttribute("canComplete", me.seesRecruiterPages() && started
                                          && interview.getStatus() == InterviewStatus.SCHEDULED);
        if (me.seesRecruiterPages() && open && !started) {
            model.addAttribute("available", interviewersApi.findAvailableInterviewers(interview.getScheduledAt(), id));
        }
        return "interviews/detail";
    }

    @PostMapping("/interviews/{id}/feedback")
    public String submitFeedback(@PathVariable long id, @RequestParam String feedback, @RequestParam boolean inclined,
                                 RedirectAttributes redirect) throws ApiException {
        interviewsApi.submitFeedback(id, new FeedbackCreate().feedback(feedback).inclined(inclined));
        return WebControllerAdvice.done(redirect, "Thanks, your feedback was saved", "/interviews/" + id);
    }

    @PostMapping("/interviews/{id}/assign")
    public String assign(@PathVariable long id, @RequestParam long interviewerId,
                         @RequestParam(required = false) Long shadowId, RedirectAttributes redirect) throws ApiException {
        interviewsApi.assignInterviewers(id, new InterviewAssignment().interviewerId(interviewerId).shadowId(shadowId));
        return WebControllerAdvice.done(redirect, "Interview scheduled", "/interviews/" + id);
    }

    @PostMapping("/interviews/{id}/unassign")
    public String unassign(@PathVariable long id, RedirectAttributes redirect) throws ApiException {
        interviewsApi.unassignInterviewers(id);
        return WebControllerAdvice.done(redirect, "Interviewers removed", "/interviews/" + id);
    }

    @PostMapping("/interviews/{id}/reschedule")
    public String reschedule(@PathVariable long id, @RequestParam String scheduledAt, RedirectAttributes redirect)
            throws ApiException {
        Interview interview = interviewsApi.rescheduleInterview(id,
                new InterviewSchedule().scheduledAt(Fmt.parse(scheduledAt)));
        String message = interview.getInterviewerId() == null
                ? "Interview moved; the interviewer is not available then, so find a new one"
                : "Interview moved";
        return WebControllerAdvice.done(redirect, message, "/interviews/" + id);
    }

    @PostMapping("/interviews/{id}/cancel")
    public String cancel(@PathVariable long id, RedirectAttributes redirect) throws ApiException {
        interviewsApi.cancelInterview(id);
        return WebControllerAdvice.done(redirect, "Interview cancelled", "/interviews/" + id);
    }

    @PostMapping("/interviews/{id}/complete")
    public String complete(@PathVariable long id, RedirectAttributes redirect) throws ApiException {
        interviewsApi.completeInterview(id);
        return WebControllerAdvice.done(redirect, "Interview marked as completed", "/interviews/" + id);
    }
}
