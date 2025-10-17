package com.deadlinr.app.service;

import com.deadlinr.app.config.AppFeatures;
import com.deadlinr.app.dto.AuthResponse;
import com.deadlinr.app.dto.LoginRequest;
import com.deadlinr.app.dto.RegisterRequest;
import com.deadlinr.app.dto.UserResponse;
import com.deadlinr.app.entity.Role;
import com.deadlinr.app.entity.User;
import com.deadlinr.app.exception.EmailAlreadyExistsException;
import com.deadlinr.app.exception.InvalidRefreshTokenException;
import com.deadlinr.app.exception.ResourceNotFoundException;
import com.deadlinr.app.repository.UserRepository;
import com.deadlinr.app.security.AuthCookieService;
import com.deadlinr.app.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final AuthCookieService authCookieService;
    private final AppFeatures features;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       RefreshTokenService refreshTokenService,
                       AuthCookieService authCookieService,
                       AppFeatures features) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        this.refreshTokenService = refreshTokenService;
        this.authCookieService = authCookieService;
        this.features = features;
    }

    public AuthResponse register(RegisterRequest request, HttpServletResponse response) {
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered: " + request.email());
        }

        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .group(request.group())
                .role(Role.STUDENT)
                .build();

        userRepository.save(user);

        return issueSession(user, response);
    }

    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isPasswordLoginEnabled()) {
            throw new BadCredentialsException("Invalid email or password");
        }
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        return issueSession(user, response);
    }

    public void issueSocialSession(User user, HttpServletResponse response) {
        ensureRefreshTokensEnabled();
        issueSession(user, response);
    }

    public AuthResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        ensureRefreshTokensEnabled();

        String raw = authCookieService.readRefresh(request)
                .orElseThrow(() -> new InvalidRefreshTokenException("Missing refresh token"));

        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(raw);
        authCookieService.setAccess(response, jwtService.generateAccessToken(rotated.user()));
        authCookieService.setRefresh(response, rotated.raw());

        return new AuthResponse(null, UserResponse.from(rotated.user()));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authCookieService.readRefresh(request).ifPresent(refreshTokenService::revoke);
        authCookieService.clearAll(response);
    }

    public void logoutAll(User user, HttpServletResponse response) {
        ensureRefreshTokensEnabled();
        refreshTokenService.revokeAllForUser(user);
        authCookieService.clearAll(response);
    }

    private AuthResponse issueSession(User user, HttpServletResponse response) {
        if (features.refreshTokens()) {
            authCookieService.setAccess(response, jwtService.generateAccessToken(user));
            authCookieService.setRefresh(response, refreshTokenService.issue(user));
            return new AuthResponse(null, UserResponse.from(user));
        }
        // Legacy mode (feature flag off): long-lived token in the response body.
        return new AuthResponse(jwtService.generateAccessToken(user), UserResponse.from(user));
    }

    private void ensureRefreshTokensEnabled() {
        if (!features.refreshTokens()) {
            throw new ResourceNotFoundException("Refresh tokens are disabled (app.features.refresh-tokens=false)");
        }
    }
}
