package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.ApplicationsApi;
import com.locohiring.client.api.CandidatesApi;
import com.locohiring.client.api.FunnelsApi;
import com.locohiring.client.api.PositionsApi;
import com.locohiring.client.model.Application;
import com.locohiring.client.model.ApplicationCreate;
import com.locohiring.client.model.Candidate;
import com.locohiring.client.model.CandidateCreate;
import com.locohiring.client.model.Resume;
import com.locohiring.web.auth.WebUser;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class CandidatesController {

    private final CandidatesApi candidatesApi;
    private final PositionsApi positionsApi;
    private final FunnelsApi funnelsApi;
    private final ApplicationsApi applicationsApi;

    public CandidatesController(CandidatesApi candidatesApi, PositionsApi positionsApi, FunnelsApi funnelsApi,
                                ApplicationsApi applicationsApi) {
        this.candidatesApi = candidatesApi;
        this.positionsApi = positionsApi;
        this.funnelsApi = funnelsApi;
        this.applicationsApi = applicationsApi;
    }

    @GetMapping("/candidates")
    public String list(@ModelAttribute("me") WebUser me, Model model) throws ApiException {
        model.addAttribute("candidates", candidatesApi.listCandidates());
        if (me.seesRecruiterPages()) {
            model.addAttribute("positions", positionsApi.listPositions());
        }
        return "candidates/list";
    }

    @PostMapping("/candidates")
    public String create(@RequestParam String email, @RequestParam String firstName,
                         @RequestParam(required = false) String surname, @RequestParam long positionId,
                         RedirectAttributes redirect) throws ApiException {
        Candidate candidate = candidatesApi.createCandidate(new CandidateCreate().email(email).firstName(firstName)
                .surname(RecruiterController.blankToNull(surname)).positionId(positionId));
        return WebControllerAdvice.done(redirect, "Candidate registered", "/candidates/" + candidate.getId());
    }

    @GetMapping("/candidates/{id}")
    public String detail(@PathVariable long id, @ModelAttribute("me") WebUser me, Model model) throws ApiException {
        model.addAttribute("candidate", candidatesApi.getCandidate(id));
        model.addAttribute("applications", candidatesApi.listCandidateApplications(id));
        if (me.seesRecruiterPages()) {
            model.addAttribute("funnels", funnelsApi.listFunnels());
        }
        return "candidates/detail";
    }

    @PostMapping("/candidates/{id}/applications")
    public String startApplication(@PathVariable long id, @RequestParam long funnelId, RedirectAttributes redirect)
            throws ApiException {
        Application application = applicationsApi.createApplication(
                new ApplicationCreate().candidateId(id).funnelId(funnelId));
        return WebControllerAdvice.done(redirect, "Application started", "/applications/" + application.getId());
    }

    @PostMapping("/candidates/{id}/resume")
    public String uploadResume(@PathVariable long id, @RequestParam MultipartFile file, RedirectAttributes redirect)
            throws ApiException, IOException {
        if (file.isEmpty()) {
            redirect.addFlashAttribute("error", "Choose a file to upload");
            return "redirect:/candidates/" + id;
        }
        candidatesApi.uploadResume(id, new Resume()
                .filename(file.getOriginalFilename() == null ? "resume" : file.getOriginalFilename())
                .contentType(file.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.getContentType())
                .content(file.getBytes()));
        return WebControllerAdvice.done(redirect, "Resume uploaded", "/candidates/" + id);
    }

    @GetMapping("/candidates/{id}/resume")
    public ResponseEntity<byte[]> downloadResume(@PathVariable long id) throws ApiException {
        Resume resume = candidatesApi.getResume(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(resume.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(resume.getFilename()).build().toString())
                .body(resume.getContent());
    }
}
