package com.yingyou.assessment;

import com.yingyou.account.GuestSessionService;
import com.yingyou.account.Learner;
import com.yingyou.assessment.AssessmentRequests.CreateAssessmentRequest;
import com.yingyou.assessment.AssessmentRequests.SubmitAnswerRequest;
import com.yingyou.assessment.AssessmentViews.HistoryView;
import com.yingyou.assessment.AssessmentViews.ResultView;
import com.yingyou.assessment.AssessmentViews.SessionView;
import com.yingyou.shared.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class AssessmentController {

    private final AssessmentService assessmentService;
    private final GuestSessionService guestSessionService;

    public AssessmentController(AssessmentService assessmentService, GuestSessionService guestSessionService) {
        this.assessmentService = assessmentService;
        this.guestSessionService = guestSessionService;
    }

    @PostMapping("/api/v1/vocabulary-assessments")
    public ResponseEntity<SessionView> create(@Valid @RequestBody CreateAssessmentRequest request,
                                              HttpSession session) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(assessmentService.createSession(learnerId(session), request));
    }

    @GetMapping("/api/v1/vocabulary-assessments/{id}")
    public SessionView get(@PathVariable UUID id, HttpSession session) {
        return assessmentService.getSession(learnerId(session), id);
    }

    @PostMapping("/api/v1/vocabulary-assessments/{id}/answers")
    public SessionView answer(@PathVariable UUID id,
                              @Valid @RequestBody SubmitAnswerRequest request,
                              HttpSession session) {
        return assessmentService.submitAnswer(learnerId(session), id, request);
    }

    @GetMapping("/api/v1/vocabulary-assessments/{id}/result")
    public ResponseEntity<ResultView> result(@PathVariable UUID id, HttpSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(assessmentService.result(learnerId(session), id));
    }

    @GetMapping("/api/v1/me/vocabulary-assessments")
    public List<HistoryView> history(HttpSession session) {
        return assessmentService.history(learnerId(session));
    }

    private UUID learnerId(HttpSession session) {
        return guestSessionService.currentLearner(session)
                .map(Learner::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "需要有效的学习者会话"));
    }
}
