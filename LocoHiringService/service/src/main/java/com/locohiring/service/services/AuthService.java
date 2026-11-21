package com.locohiring.service.services;

import com.locohiring.api.model.CurrentUser;
import com.locohiring.api.model.TokenResponse;
import com.locohiring.service.ApiException;
import com.locohiring.service.dao.UserDao;
import com.locohiring.service.dao.UserDao.StoredUser;
import com.locohiring.service.security.AuthUser;
import com.locohiring.service.security.JwtTokenProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger LOG = LogManager.getLogger(AuthService.class);

    private final UserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokens;

    public AuthService(UserDao userDao, PasswordEncoder passwordEncoder, JwtTokenProvider tokens) {
        this.userDao = userDao;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    public TokenResponse login(String email, String password) {
        StoredUser stored = userDao.findByEmail(email.trim())
                .filter(u -> u.active() && passwordEncoder.matches(password, u.passwordHash()))
                .orElseThrow(() -> {
                    LOG.info("Failed login for {}", email);
                    return new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
                });
        LOG.info("{} logged in", stored.user().getEmail());
        return issue(stored);
    }

    /** Issues new tokens; the user is read again so that deactivated users cannot refresh. */
    public TokenResponse refresh(String refreshToken) {
        StoredUser stored = tokens.parseRefreshToken(refreshToken)
                .flatMap(user -> userDao.findByEmail(user.email()))
                .filter(StoredUser::active)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
        return issue(stored);
    }

    public CurrentUser currentUser(AuthUser user) {
        return userDao.findByEmail(user.email()).map(StoredUser::user)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    }

    private TokenResponse issue(StoredUser stored) {
        AuthUser user = stored.toAuthUser();
        return new TokenResponse()
                .accessToken(tokens.createAccessToken(user))
                .refreshToken(tokens.createRefreshToken(user))
                .expiresIn((int) tokens.accessTtl().toSeconds())
                .user(stored.user());
    }
}
