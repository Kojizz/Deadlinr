package com.deadlinr.app.security;

import com.deadlinr.app.config.AppFeatures;
import com.deadlinr.app.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.function.Function;

@Service
public class JwtService {

    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_EPOCH = "epoch";
    public static final String TYPE_ACCESS = "access";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AppFeatures features;

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-expiration}")
    private long accessExpiration;

    @Value("${app.jwt.legacy-expiration:86400000}")
    private long legacyExpiration;

    @Value("${app.jwt.refresh-expiration}")
    private long refreshExpiration;

    public JwtService(AppFeatures features) {
        this.features = features;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Short-lived access token. In cookie mode it also carries the user's
     * auth_epoch so "log out everywhere" invalidates it immediately.
     */
    public String generateAccessToken(User user) {
        if (!features.refreshTokens()) {
            return Jwts.builder()
                    .subject(user.getUsername())
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + legacyExpiration))
                    .signWith(getSigningKey())
                    .compact();
        }
        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_EPOCH, user.getAuthEpoch())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /** Opaque refresh token (cryptographically random, stored hashed). */
    public String generateRefreshToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 hex digest; only hashes are persisted. */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public long getRefreshExpiration() {
        return refreshExpiration;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * In cookie mode the token must be a typed access token whose epoch still
     * matches the user row; legacy mode accepts any non-expired token for the user.
     */
    public boolean isTokenValid(String token, User user) {
        final String username = extractUsername(token);
        if (!username.equals(user.getUsername()) || isTokenExpired(token)) {
            return false;
        }
        if (features.refreshTokens()) {
            Claims claims = extractAllClaims(token);
            return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))
                    && Long.valueOf(user.getAuthEpoch()).equals(claims.get(CLAIM_EPOCH, Long.class));
        }
        return true;
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Whether the token is syntactically invalid/expired (vs. semantically rejected). */
    public boolean isMalformed(String token) {
        try {
            extractAllClaims(token);
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
