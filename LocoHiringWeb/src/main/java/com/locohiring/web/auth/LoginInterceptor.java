package com.locohiring.web.auth;

import com.locohiring.client.ApiException;
import com.locohiring.client.api.AuthApi;
import com.locohiring.client.model.RefreshRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;

/**
 * Sends anonymous visitors to the login page and renews the 5-minute access token with the refresh token shortly
 * before it expires, so users stay logged in while they use the site.
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(LoginInterceptor.class);
    private static final long RENEW_SECONDS_BEFORE_EXPIRY = 30;

    private final AuthApi authApi;

    public LoginInterceptor(AuthApi authApi) {
        this.authApi = authApi;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        WebUser user = session == null ? null : (WebUser) session.getAttribute(WebUser.SESSION_KEY);
        if (user == null) {
            response.sendRedirect("/login");
            return false;
        }
        if (Instant.now().plusSeconds(RENEW_SECONDS_BEFORE_EXPIRY).isAfter(user.accessTokenExpiresAt())) {
            try {
                session.setAttribute(WebUser.SESSION_KEY,
                        WebUser.from(authApi.refresh(new RefreshRequest().refreshToken(user.refreshToken()))));
            } catch (ApiException e) {
                LOG.info("Could not refresh the session of {}: {}", user.email(), e.getCode());
                session.invalidate();
                response.sendRedirect("/login?expired");
                return false;
            }
        }
        return true;
    }
}
