package com.yingyou.personalization;

import com.yingyou.account.GuestSessionService;
import com.yingyou.account.Learner;
import com.yingyou.personalization.PersonalizationRequests.CreatePersonalizedSessionRequest;
import com.yingyou.personalization.PersonalizationRequests.UpdatePreferencesRequest;
import com.yingyou.personalization.PersonalizationViews.BatchView;
import com.yingyou.personalization.PersonalizationViews.PreferencesView;
import com.yingyou.shared.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PersonalizationController {

    private final PersonalizationService personalizationService;
    private final GuestSessionService guestSessionService;

    public PersonalizationController(PersonalizationService personalizationService,
                                     GuestSessionService guestSessionService) {
        this.personalizationService = personalizationService;
        this.guestSessionService = guestSessionService;
    }

    @GetMapping("/me/learning-preferences")
    public PreferencesView preferences(HttpSession session) {
        return personalizationService.preferences(learnerId(session));
    }

    @PatchMapping("/me/learning-preferences")
    public PreferencesView updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request,
                                             HttpSession session) {
        return personalizationService.updatePreferences(learnerId(session), request);
    }

    @PostMapping("/personalized-practice/sessions")
    public BatchView createSession(@Valid @RequestBody CreatePersonalizedSessionRequest request,
                                   HttpSession session) {
        return personalizationService.createSession(learnerId(session), request);
    }

    private UUID learnerId(HttpSession session) {
        return guestSessionService.currentLearner(session)
                .map(Learner::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "需要有效的学习者会话"));
    }
}
