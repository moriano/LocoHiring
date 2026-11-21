package com.locohiring.web.controllers;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.AuthApi;
import com.locohiring.client.model.LoginRequest;
import com.locohiring.web.auth.WebUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final AuthApi authApi;

    public LoginController(AuthApi authApi) {
        this.authApi = authApi;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        return session.getAttribute(WebUser.SESSION_KEY) != null ? "redirect:/" : "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password, HttpServletRequest request,
                        Model model) {
        try {
            WebUser user = WebUser.from(authApi.login(new LoginRequest().email(email).password(password)));
            request.getSession();
            request.changeSessionId();
            request.getSession().setAttribute(WebUser.SESSION_KEY, user);
            return "redirect:/";
        } catch (ApiException e) {
            model.addAttribute("error", e.getCode() == 401 ? "Invalid email or password" : WebControllerAdvice.message(e));
            model.addAttribute("email", email);
            return "login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/")
    public String home(HttpSession session) {
        WebUser user = (WebUser) session.getAttribute(WebUser.SESSION_KEY);
        return user.seesRecruiterPages() ? "redirect:/recruiter" : "redirect:/interviewer";
    }
}
