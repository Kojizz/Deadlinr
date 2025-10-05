package com.deadlinr.app.service;

import com.deadlinr.app.entity.RefreshToken;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.InvalidRefreshTokenException;
import com.deadlinr.app.repository.RefreshTokenRepository;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.JwtService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Rotating opaque refresh tokens.
 * <p>
 * Rotation: every use revokes the presented token and issues a fresh one in the
 * same family. Reuse detection: presenting an already-revoked token is treated
 * as token theft - the whole family is revoked and the user's auth_epoch is
 * bumped, which also invalidates every outstanding access token.
 * <p>
 * Only SHA-256 hashes are persisted; the raw token exists only in the cookie.
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               UserRepository userRepository,
                               JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    /** Creates a fresh token for the user, starting a new rotation family. */
    @Transactional
    public String issue(User user) {
        return persistNew(user, UUID.randomUUID()).raw();
    }

    /** Rotates a presented token; on reuse, nukes the family and the session.
     *  noRollbackFor: the revocation/epoch-bump writes must survive even though
     *  the method signals the reuse by throwing. */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedToken rotate(String rawToken) {
        RefreshToken presented = refreshTokenRepository
                .findByTokenHash(jwtService.hashToken(rawToken))
                .orElseThrow(() -> new InvalidRefreshTokenException("Unknown refresh token"));

        if (presented.getRevokedAt() != null) {
            // Reuse of a rotated token: possible theft. Kill the family and
            // bump the epoch so all access tokens die too. (revokeFamily
            // clears the persistence context, so reload the user by id
            // instead of touching the detached lazy proxy.)
            Long userId = presented.getUser().getId();
            refreshTokenRepository.revokeFamily(presented.getFamilyId(), LocalDateTime.now());
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new InvalidRefreshTokenException("User not found"));
            user.setAuthEpoch(user.getAuthEpoch() + 1);
            userRepository.save(user);
            throw new InvalidRefreshTokenException("Refresh token reuse detected; session revoked");
        }

        if (presented.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        IssuedToken replacement = persistNew(presented.getUser(), presented.getFamilyId());
        presented.setReplacedBy(replacement.row());
        presented.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(presented);

        // Touch the fields now: the caller uses the user outside this transaction
        // and a lazy proxy would throw LazyInitializationException there.
        User owner = presented.getUser();
        owner.getAuthEpoch();
        owner.getEmail();
        return new RotatedToken(replacement.raw(), owner);
    }

    /** Revokes a single presented token (used by logout). Unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawToken))
                .ifPresent(token -> {
                    token.setRevokedAt(LocalDateTime.now());
                    refreshTokenRepository.save(token);
                });
    }

    /** "Log out everywhere": revokes all of the user's refresh tokens and bumps the epoch. */
    @Transactional
    public void revokeAllForUser(User user) {
        refreshTokenRepository.revokeAllForUser(user.getId(), LocalDateTime.now());
        user.setAuthEpoch(user.getAuthEpoch() + 1);
        userRepository.save(user);
    }

    /** Persists a new token (hash only) and returns the raw value for the cookie. */
    private IssuedToken persistNew(User user, UUID familyId) {
        String raw = jwtService.generateRefreshToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(jwtService.hashToken(raw))
                .familyId(familyId)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshExpiration() / 1000))
                .build();
        refreshTokenRepository.save(token);
        return new IssuedToken(raw, token);
    }

    private record IssuedToken(String raw, RefreshToken row) {
    }

    /** Result of a successful rotation: the new raw token and its owner. */
    public record RotatedToken(String raw, User user) {
    }
}
