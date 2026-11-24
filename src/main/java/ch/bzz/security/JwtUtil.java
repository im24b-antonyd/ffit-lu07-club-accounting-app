package ch.bzz.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    private static final long TOKEN_VALIDITY_MS = 3_600_000L;
    private static final String BEARER_PREFIX = "Bearer ";

    @Value("${jwt.secret}")
    private String secret;

    private Key key;

    @PostConstruct
    public void init() {
        key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String projectName) {
        Date currentTime = new Date();
        Date expirationTime = new Date(currentTime.getTime() + TOKEN_VALIDITY_MS);

        return Jwts.builder()
                .setSubject(projectName)
                .setIssuer("AccountingApp")
                .setIssuedAt(currentTime)
                .setExpiration(expirationTime)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public String extractProjectName(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts and validates the Bearer JWT from the HTTP request and returns its subject (projectName).
     */
    public String resolveProjectName(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
        }
        String token = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            String subject = extractProjectName(token);
            if (subject == null || subject.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token subject is missing");
            }
            return subject;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("JWT verification failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
