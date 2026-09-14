package com.haodaone.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * Issues and validates JWTs. Access tokens are short-lived (default 15 min)
 * and carry the user's roles as a claim so the JwtAuthenticationFilter can
 * build a Spring Security Authentication without hitting the DB on every
 * request. Refresh tokens are longer-lived, persisted (see auth.entity.
 * RefreshToken), and can be revoked - that's what makes "logout everywhere"
 * and session management possible, which a stateless access token alone
 * can't give you.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-minutes:15}") long accessTokenExpiryMinutes) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiryMs = accessTokenExpiryMinutes * 60 * 1000;
    }

    public String generateAccessToken(String username, List<String> roles) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenExpiryMs);
        return Jwts.builder()
                .subject(username)
                .claim("roles", roles)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractRoles(String token) {
        return extractClaim(token, claims -> (List<String>) claims.get("roles"));
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        try {
            String username = extractUsername(token);
            return username.equals(expectedUsername) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException ex) {
            // Avoid logging exception messages which may contain sensitive details
            log.debug("Token validation failed: {}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private boolean isExpired(String token) {
        try {
            return extractClaim(token, Claims::getExpiration).before(new Date());
        } catch (ExpiredJwtException ex) {
            return true;
        }
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }
}
