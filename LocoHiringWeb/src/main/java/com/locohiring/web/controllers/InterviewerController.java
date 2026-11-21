package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.AvailabilityApi;
import com.locohiring.client.api.InterviewersApi;
import com.locohiring.client.api.MeApi;
import com.locohiring.client.model.AvailabilityBlock;
import com.locohiring.client.model.AvailabilityCreate;
import com.locohiring.web.auth.WebUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** The interviewer experience: landing page and calendar. */
@Controller
public class InterviewerController {

    private static final int CALENDAR_WEEKS = 4;
    private static final int RECURRING_WEEKS = 8;

    private final MeApi meApi;
    private final AvailabilityApi availabilityApi;
    private final InterviewersApi interviewersApi;

    public InterviewerController(MeApi meApi, AvailabilityApi availabilityApi, InterviewersApi interviewersApi) {
        this.meApi = meApi;
        this.availabilityApi = availabilityApi;
        this.interviewersApi = interviewersApi;
    }

    @GetMapping("/interviewer")
    public String dashboard(Model model) throws ApiException {
        model.addAttribute("upcoming", meApi.listMyUpcomingInterviews());
        model.addAttribute("pending", meApi.listMyPendingFeedback());
        model.addAttribute("debriefs", meApi.listMyUpcomingDebriefs());
        return "interviewer/dashboard";
    }

    /** Four weeks of availability blocks; admins pick whose calendar to see. */
    @GetMapping("/calendar")
    public String calendar(@RequestParam(defaultValue = "0") int week,
                           @RequestParam(required = false) Long interviewerId,
                           @ModelAttribute("me") WebUser me, Model model) throws ApiException {
        if (me.admin()) {
            model.addAttribute("interviewers", interviewersApi.listInterviewers());
            if (interviewerId == null && !me.interviewer()) {
                model.addAttribute("week", week);
                return "interviewer/calendar";
            }
        }
        LocalDate from = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(week);
        LocalDate to = from.plusWeeks(CALENDAR_WEEKS).minusDays(1);
        List<AvailabilityBlock> blocks = availabilityApi.listAvailability(from, to, interviewerId);
        Map<LocalDate, List<AvailabilityBlock>> byDay = blocks.stream()
                .collect(Collectors.groupingBy(AvailabilityBlock::getDate, TreeMap::new, Collectors.toList()));
        model.addAttribute("byDay", byDay);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("week", week);
        model.addAttribute("interviewerId", interviewerId);
        model.addAttribute("today", LocalDate.now());
        return "interviewer/calendar";
    }

    @PostMapping("/calendar")
    public String addAvailability(@RequestParam LocalDate date, @RequestParam int startHour, @RequestParam int endHour,
                                  @RequestParam(defaultValue = "false") boolean recurring,
                                  @RequestParam(required = false) Long interviewerId,
                                  RedirectAttributes redirect) throws ApiException {
        List<AvailabilityBlock> created = availabilityApi.createAvailability(new AvailabilityCreate()
                .date(date).startHour(startHour).endHour(endHour)
                .weeks(recurring ? RECURRING_WEEKS : 1)
                .interviewerId(interviewerId));
        String message = created.isEmpty() ? "Those hours were already in your calendar"
                : "Added " + created.size() + " one-hour block" + (created.size() == 1 ? "" : "s")
                  + (recurring ? " over the next " + RECURRING_WEEKS + " weeks" : "");
        return WebControllerAdvice.done(redirect, message, calendarUrl(interviewerId));
    }

    @PostMapping("/calendar/{blockId}/delete")
    public String deleteAvailability(@PathVariable long blockId, @RequestParam(required = false) Long interviewerId,
                                     RedirectAttributes redirect) throws ApiException {
        availabilityApi.deleteAvailability(blockId);
        return WebControllerAdvice.done(redirect, "Block removed", calendarUrl(interviewerId));
    }

    private static String calendarUrl(Long interviewerId) {
        return interviewerId == null ? "/calendar" : "/calendar?interviewerId=" + interviewerId;
    }
}
