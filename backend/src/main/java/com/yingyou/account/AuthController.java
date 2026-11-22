package com.yingyou.account;

import com.yingyou.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final int ACCOUNT_SESSION_SECONDS = 24 * 60 * 60;

    private final GuestSessionService guestSessionService;
    private final AuthService authService;

    public AuthController(GuestSessionService guestSessionService, AuthService authService) {
        this.guestSessionService = guestSessionService;
        this.authService = authService;
    }

    @GetMapping("/session")
    public AuthSessionResponse session(HttpSession session,
                                       @RequestAttribute("_csrf") CsrfToken csrfToken) {
        return guestSessionService.currentLearner(session)
                .map(learner -> new AuthSessionResponse(learner.getKind().name(), learner.getId(), csrfToken.getToken()))
                .orElseGet(() -> new AuthSessionResponse("ANONYMOUS", null, csrfToken.getToken()));
    }

    @PostMapping("/guest")
    public AuthSessionResponse createGuest(HttpServletRequest request,
                                            @RequestAttribute("_csrf") CsrfToken csrfToken) {
        Learner learner = guestSessionService.ensureGuest(request);
        return new AuthSessionResponse(learner.getKind().name(), learner.getId(), csrfToken.getToken());
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        RegisterResponse response = authService.register(request, clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request,
                                            HttpServletRequest httpRequest) {
        authService.verifyEmail(request, clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verification-requests")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody VerificationRequest request,
                                                  HttpServletRequest httpRequest) {
        authService.resendVerification(request, clientIp(httpRequest));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                               HttpServletRequest httpRequest,
                                               @RequestAttribute("_csrf") CsrfToken csrfToken) {
        HttpSession session = httpRequest.getSession(true);
        Optional<UUID> guestId = guestSessionService.currentLearner(session)
                .filter(learner -> learner.getKind() == LearnerKind.GUEST)
                .map(Learner::getId);

        AuthService.LoginOutcome outcome = authService.login(request, guestId.orElse(null),
                clientIp(httpRequest));

        String oldSessionId = session.getId();
        authService.removeAccountSession(oldSessionId);
        httpRequest.changeSessionId();
        session.setAttribute(GuestSessionService.LEARNER_ID_ATTRIBUTE, outcome.learnerId().toString());
        session.setMaxInactiveInterval(ACCOUNT_SESSION_SECONDS);
        authService.registerAccountSession(session.getId(), outcome.learnerId());

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new LoginResponse("ACCOUNT", outcome.learnerId(), outcome.nickname(), outcome.email(),
                        outcome.guestRecordsMigrated(), csrfToken.getToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            String sessionId = session.getId();
            authService.removeAccountSession(sessionId);
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password-reset-requests")
    public ResponseEntity<PasswordResetRequestResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request,
            HttpServletRequest httpRequest) {
        PasswordResetRequestResponse response = authService.requestPasswordReset(
                request, clientIp(httpRequest));
        return ResponseEntity.accepted()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @PostMapping("/password-resets")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetSubmitRequest request,
                                              HttpServletRequest httpRequest) {
        authService.resetPassword(request, clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static ApiException unauthenticated() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习身份");
    }
}
