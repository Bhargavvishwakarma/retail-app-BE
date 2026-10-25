package com.retail.service;

import com.retail.model.AppUser;
import com.retail.model.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class ActorAssertionService {
    private final SecretKey key;

    public ActorAssertionService(@Value("${app.ai.actor-token-secret:${app.jwt.secret}}") String secret) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String mint(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getId().toString())
                .claim("role", user.getRole().name()).claim("purpose", "ai-retail-data")
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(90)))
                .signWith(key).compact();
    }

    public Actor verify(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!"ai-retail-data".equals(claims.get("purpose", String.class))) {
            throw new IllegalArgumentException("Invalid actor assertion");
        }
        return new Actor(UUID.fromString(claims.getSubject()),
                UserRole.valueOf(claims.get("role", String.class)));
    }

    public record Actor(UUID userId, UserRole role) {
    }
}
