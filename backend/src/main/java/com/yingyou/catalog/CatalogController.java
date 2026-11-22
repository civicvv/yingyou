package com.yingyou.catalog;

import com.yingyou.account.GuestSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/courses")
public class CatalogController {

    private final CatalogService catalogService;
    private final GuestSessionService guestSessionService;

    public CatalogController(CatalogService catalogService, GuestSessionService guestSessionService) {
        this.catalogService = catalogService;
        this.guestSessionService = guestSessionService;
    }

    @GetMapping
    public List<CourseSummaryResponse> listCourses(@RequestParam(required = false) String examTrack) {
        return catalogService.listPublishedCourses(examTrack);
    }

    @GetMapping("/{courseId}")
    public ResponseEntity<CourseDetailResponse> getCourse(@PathVariable String courseId,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(catalogService.getPublishedCourse(courseId, currentLearnerId(request)));
    }

    private Optional<UUID> currentLearnerId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return Optional.empty();
        }
        return guestSessionService.currentLearner(session).map(learner -> learner.getId());
    }
}
