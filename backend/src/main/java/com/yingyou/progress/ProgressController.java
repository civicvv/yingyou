package com.yingyou.progress;

import com.yingyou.account.GuestSessionService;
import com.yingyou.account.Learner;
import com.yingyou.shared.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
public class ProgressController {

    private final ProgressService progressService;
    private final GuestSessionService guestSessionService;

    public ProgressController(ProgressService progressService, GuestSessionService guestSessionService) {
        this.progressService = progressService;
        this.guestSessionService = guestSessionService;
    }

    @GetMapping("/progress")
    public ResponseEntity<CourseProgressView> courseProgress(@RequestParam String courseId,
                                                            HttpServletRequest request) {
        UUID learnerId = requireLearnerId(request);
        return noStore(progressService.getCourseProgress(learnerId, courseId));
    }

    @GetMapping("/courses")
    public ResponseEntity<List<MyCourseView>> myCourses(HttpServletRequest request) {
        UUID learnerId = requireLearnerId(request);
        return noStore(progressService.getMyCourses(learnerId));
    }

    private UUID requireLearnerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "需要有效的学习身份");
        }
        return guestSessionService.currentLearner(session)
                .map(Learner::getId)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
                        "需要有效的学习身份"));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
