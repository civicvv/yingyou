package com.yingyou.practice;

import com.yingyou.account.GuestSessionService;
import com.yingyou.account.Learner;
import com.yingyou.shared.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/practice/sessions")
public class PracticeController {

    private final PracticeService practiceService;
    private final GuestSessionService guestSessionService;

    public PracticeController(PracticeService practiceService, GuestSessionService guestSessionService) {
        this.practiceService = practiceService;
        this.guestSessionService = guestSessionService;
    }

    @PostMapping
    public PracticeSessionView create(@Valid @RequestBody CreatePracticeSessionRequest request,
                                      HttpSession session) {
        return practiceService.createSession(learnerId(session), request);
    }

    @GetMapping("/{sessionId}")
    public PracticeSessionView get(@PathVariable UUID sessionId, HttpSession session) {
        return practiceService.getSession(learnerId(session), sessionId);
    }

    @PostMapping("/{sessionId}/prefix-check")
    public PrefixCheckResponse prefixCheck(@PathVariable UUID sessionId,
                                            @Valid @RequestBody PrefixCheckRequest request,
                                            HttpSession session) {
        return practiceService.prefixCheck(learnerId(session), sessionId, request);
    }

    @PostMapping("/{sessionId}/commands")
    public PracticeCommandResponse command(@PathVariable UUID sessionId,
                                            @Valid @RequestBody PracticeCommandRequest request,
                                            HttpSession session) {
        return practiceService.command(learnerId(session), sessionId, request);
    }

    @GetMapping("/{sessionId}/result")
    public PracticeResultView result(@PathVariable UUID sessionId, HttpSession session) {
        return practiceService.getResult(learnerId(session), sessionId);
    }

    private UUID learnerId(HttpSession session) {
        return guestSessionService.currentLearner(session)
                .map(Learner::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "需要有效的学习者会话"));
    }
}
