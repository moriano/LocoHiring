package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.InterviewersApi;
import com.locohiring.client.api.JobFamiliesApi;
import com.locohiring.client.api.RecruitersApi;
import com.locohiring.client.model.Interviewer;
import com.locohiring.client.model.InterviewerCreate;
import com.locohiring.client.model.InterviewerUpdate;
import com.locohiring.client.model.JobFamily;
import com.locohiring.client.model.RecruiterCreate;
import com.locohiring.client.model.RecruiterUpdate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** Admin pages to list, create, edit and deactivate recruiters and interviewers. The service rejects non-admins. */
@Controller
public class UsersController {

    private final RecruitersApi recruitersApi;
    private final InterviewersApi interviewersApi;
    private final JobFamiliesApi jobFamiliesApi;

    public UsersController(RecruitersApi recruitersApi, InterviewersApi interviewersApi,
                           JobFamiliesApi jobFamiliesApi) {
        this.recruitersApi = recruitersApi;
        this.interviewersApi = interviewersApi;
        this.jobFamiliesApi = jobFamiliesApi;
    }

    @GetMapping("/admin/users")
    public String users(Model model) throws ApiException {
        model.addAttribute("recruiters", recruitersApi.listRecruiters());
        model.addAttribute("interviewers", interviewersApi.listInterviewers());
        model.addAttribute("jobFamilies", jobFamiliesApi.listJobFamilies());
        return "admin/users";
    }

    @PostMapping("/admin/users/recruiters")
    public String createRecruiter(@RequestParam String email, @RequestParam String firstName,
                                  @RequestParam(required = false) String surname, @RequestParam String password,
                                  @RequestParam(defaultValue = "false") boolean admin,
                                  RedirectAttributes redirect) throws ApiException {
        recruitersApi.createRecruiter(new RecruiterCreate().email(email).firstName(firstName)
                .surname(RecruiterController.blankToNull(surname)).password(password).admin(admin));
        return WebControllerAdvice.done(redirect, "Recruiter " + email + " created", "/admin/users");
    }

    @PostMapping("/admin/users/interviewers")
    public String createInterviewer(@RequestParam String email, @RequestParam String firstName,
                                    @RequestParam(required = false) String surname, @RequestParam String password,
                                    @RequestParam int level,
                                    @RequestParam(name = "jobFamilyIds", required = false) List<Long> jobFamilyIds,
                                    @RequestParam(defaultValue = "false") boolean barRaiser,
                                    @RequestParam(defaultValue = "false") boolean admin,
                                    RedirectAttributes redirect) throws ApiException {
        interviewersApi.createInterviewer(new InterviewerCreate().email(email).firstName(firstName)
                .surname(RecruiterController.blankToNull(surname)).password(password).level(level)
                .jobFamilyIds(jobFamilyIds == null ? List.of() : jobFamilyIds).barRaiser(barRaiser).admin(admin));
        return WebControllerAdvice.done(redirect, "Interviewer " + email + " created", "/admin/users");
    }

    @GetMapping("/admin/users/recruiters/{id}")
    public String editRecruiter(@PathVariable long id, Model model) throws ApiException {
        model.addAttribute("recruiter", recruitersApi.listRecruiters().stream()
                .filter(r -> r.getId() == id).findFirst().orElseThrow(() -> new ApiException(404, "Recruiter not found")));
        return "admin/edit-recruiter";
    }

    @PostMapping("/admin/users/recruiters/{id}")
    public String updateRecruiter(@PathVariable long id, @RequestParam String email, @RequestParam String firstName,
                                  @RequestParam(required = false) String surname,
                                  @RequestParam(required = false) String password,
                                  @RequestParam(defaultValue = "false") boolean admin,
                                  @RequestParam(defaultValue = "false") boolean active,
                                  RedirectAttributes redirect) throws ApiException {
        recruitersApi.updateRecruiter(id, new RecruiterUpdate().email(email).firstName(firstName)
                .surname(RecruiterController.blankToNull(surname)).password(RecruiterController.blankToNull(password))
                .admin(admin).active(active));
        return WebControllerAdvice.done(redirect, "Recruiter " + email + " saved", "/admin/users");
    }

    @GetMapping("/admin/users/interviewers/{id}")
    public String editInterviewer(@PathVariable long id, Model model) throws ApiException {
        Interviewer interviewer = interviewersApi.listInterviewers().stream()
                .filter(i -> i.getId() == id).findFirst().orElseThrow(() -> new ApiException(404, "Interviewer not found"));
        model.addAttribute("interviewer", interviewer);
        model.addAttribute("jobFamilies", jobFamiliesApi.listJobFamilies());
        model.addAttribute("selectedFamilies", interviewer.getJobFamilies().stream().map(JobFamily::getId).toList());
        return "admin/edit-interviewer";
    }

    @PostMapping("/admin/users/interviewers/{id}")
    public String updateInterviewer(@PathVariable long id, @RequestParam String email, @RequestParam String firstName,
                                    @RequestParam(required = false) String surname,
                                    @RequestParam(required = false) String password, @RequestParam int level,
                                    @RequestParam(name = "jobFamilyIds", required = false) List<Long> jobFamilyIds,
                                    @RequestParam(defaultValue = "false") boolean barRaiser,
                                    @RequestParam(defaultValue = "false") boolean admin,
                                    @RequestParam(defaultValue = "false") boolean active,
                                    RedirectAttributes redirect) throws ApiException {
        interviewersApi.updateInterviewer(id, new InterviewerUpdate().email(email).firstName(firstName)
                .surname(RecruiterController.blankToNull(surname)).password(RecruiterController.blankToNull(password))
                .level(level).jobFamilyIds(jobFamilyIds == null ? List.of() : jobFamilyIds)
                .barRaiser(barRaiser).admin(admin).active(active));
        return WebControllerAdvice.done(redirect, "Interviewer " + email + " saved", "/admin/users");
    }
}
