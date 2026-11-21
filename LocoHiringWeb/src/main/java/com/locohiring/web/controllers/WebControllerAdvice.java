package com.locohiring.web.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.locohiring.client.ApiException;
import com.locohiring.web.auth.WebUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

@ControllerAdvice
public class WebControllerAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(WebControllerAdvice.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    @ModelAttribute("me")
    public WebUser me(HttpSession session) {
        return (WebUser) session.getAttribute(WebUser.SESSION_KEY);
    }

    /**
     * An error from LocoHiringService. A failed form submission goes back to the page it came from with the
     * service's message; a page that cannot be loaded shows the error page.
     */
    @ExceptionHandler(ApiException.class)
    public ModelAndView apiException(ApiException e, HttpServletRequest request) {
        if (e.getCode() == 401) {
            request.getSession().invalidate();
            return new ModelAndView("redirect:/login?expired");
        }
        String message = message(e);
        if ("POST".equals(request.getMethod())) {
            RequestContextUtils.getOutputFlashMap(request).put("error", message);
            String referer = request.getHeader("Referer");
            return new ModelAndView("redirect:" + (referer == null ? "/" : referer));
        }
        LOG.info("{} {} failed: {} {}", request.getMethod(), request.getRequestURI(), e.getCode(), message);
        ModelAndView view = new ModelAndView("error");
        view.addObject("me", request.getSession().getAttribute(WebUser.SESSION_KEY));
        view.addObject("status", e.getCode());
        view.addObject("message", message);
        return view;
    }

    static String message(ApiException e) {
        if (e.getCode() == 0) {
            return "LocoHiringService is not reachable";
        }
        if (e.getResponseBody() == null && e.getMessage() != null) {
            return e.getMessage();
        }
        try {
            JsonNode body = JSON.readTree(e.getResponseBody());
            if (body != null && body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception ignored) {
            // Fall through to the generic message.
        }
        return "The request failed (" + e.getCode() + ")";
    }

    /** Adds a success message shown on the next page. */
    static String done(RedirectAttributes redirect, String message, String target) {
        redirect.addFlashAttribute("success", message);
        return "redirect:" + target;
    }
}
