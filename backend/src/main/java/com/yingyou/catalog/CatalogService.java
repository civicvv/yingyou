package com.yingyou.catalog;

import com.yingyou.progress.LessonProgressView;
import com.yingyou.progress.ProgressService;
import com.yingyou.shared.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CatalogService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final ProgressService progressService;

    public CatalogService(CourseRepository courseRepository,
                          LessonRepository lessonRepository,
                          ProgressService progressService) {
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.progressService = progressService;
    }

    @Transactional(readOnly = true)
    public List<CourseSummaryResponse> listPublishedCourses(String examTrack) {
        List<Course> courses = examTrack == null || examTrack.isBlank()
                ? courseRepository.findByStatusOrderBySortOrderAsc(CourseStatus.PUBLISHED)
                : courseRepository.findByStatusAndExamTrackOrderBySortOrderAsc(
                        CourseStatus.PUBLISHED, parseExamTrack(examTrack));

        return courses.stream()
                .map(course -> new CourseSummaryResponse(
                        course.getId(),
                        course.getExamTrack().name(),
                        course.getTitle(),
                        course.getSummary(),
                        lessonRepository.countByCourseId(course.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getPublishedCourse(String courseId, Optional<UUID> learnerId) {
        Course course = courseRepository.findById(courseId)
                .filter(found -> found.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "课程不存在或尚未发布"));

        List<LessonResponse> lessons = progressService.lessonProgressForCourse(learnerId, course.getId()).stream()
                .map(this::toLessonResponse)
                .toList();

        return new CourseDetailResponse(
                course.getId(),
                course.getExamTrack().name(),
                course.getTitle(),
                course.getSummary(),
                lessons);
    }

    private LessonResponse toLessonResponse(LessonProgressView view) {
        return new LessonResponse(
                view.lessonId(),
                view.title(),
                view.sortOrder(),
                view.questionCount(),
                view.status(),
                view.lastActiveSessionId(),
                view.lastCompletedSessionId(),
                view.continueSessionId());
    }

    private ExamTrack parseExamTrack(String examTrack) {
        try {
            return ExamTrack.valueOf(examTrack.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest("INVALID_EXAM_TRACK", "不支持的考试路径：" + examTrack);
        }
    }
}
