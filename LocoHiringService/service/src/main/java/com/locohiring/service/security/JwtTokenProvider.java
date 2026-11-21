package com.locohiring.service.security;

import com.locohiring.api.model.UserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;

/** Issues and verifies the HS256 access and refresh tokens. */
@Component
public class JwtTokenProvider {

    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtTokenProvider(@Value("${locohiring.jwt.secret}") String secret,
                            @Value("${locohiring.jwt.access-ttl}") Duration accessTtl,
                            @Value("${locohiring.jwt.refresh-ttl}") Duration refreshTtl,
                            Clock clock) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.clock = clock;
    }

    public Duration accessTtl() {
        return accessTtl;
    }

    public String createAccessToken(AuthUser user) {
        return create(user, ACCESS, accessTtl);
    }

    public String createRefreshToken(AuthUser user) {
        return create(user, REFRESH, refreshTtl);
    }

    public Optional<AuthUser> parseAccessToken(String token) {
        return parse(token, ACCESS);
    }

    public Optional<AuthUser> parseRefreshToken(String token) {
        return parse(token, REFRESH);
    }

    private String create(AuthUser user, String tokenType, Duration ttl) {
        Date now = Date.from(clock.instant());
        return Jwts.builder()
                .subject(user.email())
                .claim("uid", user.id())
                .claim("type", user.type().getValue())
                .claim("admin", user.admin())
                .claim("tokenType", tokenType)
                .issuedAt(now)
                .expiration(Date.from(clock.instant().plus(ttl)))
                .signWith(key)
                .compact();
    }

    private Optional<AuthUser> parse(String token, String expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get("tokenType", String.class))) {
                return Optional.empty();
            }
            return Optional.of(new AuthUser(
                    claims.get("uid", Long.class),
                    claims.getSubject(),
                    UserType.fromValue(claims.get("type", String.class)),
                    Boolean.TRUE.equals(claims.get("admin", Boolean.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
