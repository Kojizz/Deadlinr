package com.deadlinr.app.service;

import com.deadlinr.app.entity.OAuthIdentity;
import com.deadlinr.app.entity.Role;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.repository.OAuthIdentityRepository;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.oauth.OAuthProfile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class OAuthAccountService {
    private final OAuthIdentityRepository identities;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public OAuthAccountService(OAuthIdentityRepository identities, UserRepository users, PasswordEncoder encoder) {
        this.identities = identities; this.users = users; this.encoder = encoder;
    }

    @Transactional
    public User login(String provider, OAuthProfile profile) {
        validateIdentity(provider, profile);
        var existing = identities.findByProviderAndSubject(provider, profile.subject());
        if (existing.isPresent()) return existing.get().getUser();
        String email = verifiedEmail(profile);
        if (users.existsByEmailIgnoreCase(email)) throw OAuthProfile.error("email_conflict");
        String name = profile.fullName() == null || profile.fullName().isBlank() ? "Student" : profile.fullName().trim();
        if (name.length() > 255) name = name.substring(0, 255);
        User user = users.save(User.builder().email(email).fullName(name).role(Role.STUDENT)
                .password(encoder.encode(UUID.randomUUID() + ":" + UUID.randomUUID()))
                .passwordLoginEnabled(false).build());
        identities.save(OAuthIdentity.builder().provider(provider).subject(profile.subject()).user(user).build());
        return user;
    }

    @Transactional
    public User link(String provider, OAuthProfile profile, Long ownerId, long epoch) {
        validateIdentity(provider, profile);
        verifiedEmail(profile);
        User owner = users.lockById(ownerId).orElseThrow(() -> OAuthProfile.error("session_changed"));
        if (owner.getAuthEpoch() != epoch) throw OAuthProfile.error("session_changed");
        var existing = identities.findByProviderAndSubject(provider, profile.subject());
        if (existing.isPresent()) {
            if (!existing.get().getUser().getId().equals(ownerId)) throw OAuthProfile.error("identity_conflict");
            return owner;
        }
        if (identities.findByUserIdAndProvider(ownerId, provider).isPresent()) throw OAuthProfile.error("identity_conflict");
        identities.save(OAuthIdentity.builder().provider(provider).subject(profile.subject()).user(owner).build());
        return owner;
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> linked(Long ownerId) {
        var links = identities.findByUserId(ownerId);
        return Map.of("google", links.stream().anyMatch(link -> "google".equals(link.getProvider())),
                "github", links.stream().anyMatch(link -> "github".equals(link.getProvider())));
    }

    @Transactional
    public void unlink(Long ownerId, String provider) {
        User owner = users.lockById(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var link = identities.findByUserIdAndProvider(ownerId, provider);
        if (link.isEmpty()) return;
        if (!owner.isPasswordLoginEnabled() && identities.countByUserId(ownerId) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Keep at least one sign-in method connected.");
        }
        identities.delete(link.get());
    }

    private void validateIdentity(String provider, OAuthProfile profile) {
        if (!("google".equals(provider) || "github".equals(provider)) || profile.subject() == null
                || profile.subject().isBlank() || profile.subject().length() > 255 || !profile.subject().equals(profile.subject().trim())) {
            throw OAuthProfile.error("failed");
        }
    }

    private String verifiedEmail(OAuthProfile profile) {
        if (!profile.verified() || profile.email() == null) throw OAuthProfile.error("email_unverified");
        String email = profile.email().trim().toLowerCase(Locale.ROOT);
        if (email.length() > 255 || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")) throw OAuthProfile.error("email_unverified");
        return email;
    }
}
