package com.yingyou.account;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final AuthService authService;
    private final GuestSessionService guestSessionService;

    public MeController(AuthService authService, GuestSessionService guestSessionService) {
        this.authService = authService;
        this.guestSessionService = guestSessionService;
    }

    @GetMapping
    public MeResponse me(HttpServletRequest request) {
        return authService.profile(learnerId(request));
    }

    @PatchMapping
    public MeResponse update(@Valid @RequestBody UpdateProfileRequest update, HttpServletRequest request) {
        return authService.updateProfile(learnerId(request), update);
    }

    private java.util.UUID learnerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw AuthController.unauthenticated();
        }
        return guestSessionService.currentLearner(session)
                .map(Learner::getId)
                .orElseThrow(AuthController::unauthenticated);
    }
}
