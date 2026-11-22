package com.yingyou.progress;

import com.yingyou.catalog.Course;
import com.yingyou.catalog.CourseRepository;
import com.yingyou.catalog.CourseStatus;
import com.yingyou.catalog.Lesson;
import com.yingyou.catalog.LessonRepository;
import com.yingyou.catalog.LessonVersionItemRepository;
import com.yingyou.practice.PracticeSession;
import com.yingyou.practice.PracticeSessionRepository;
import com.yingyou.shared.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    private final LearnerLessonProgressRepository progressRepository;
    private final PracticeSessionRepository sessionRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonVersionItemRepository lessonVersionItemRepository;

    public ProgressService(LearnerLessonProgressRepository progressRepository,
                           PracticeSessionRepository sessionRepository,
                           CourseRepository courseRepository,
                           LessonRepository lessonRepository,
                           LessonVersionItemRepository lessonVersionItemRepository) {
        this.progressRepository = progressRepository;
        this.sessionRepository = sessionRepository;
        this.courseRepository = courseRepository;
        this.lessonRepository = lessonRepository;
        this.lessonVersionItemRepository = lessonVersionItemRepository;
    }

    public void touchLesson(UUID learnerId, String lessonId, UUID sessionId) {
        progressRepository.touch(learnerId, lessonId, sessionId, Instant.now());
    }

    public void completeLesson(UUID learnerId, String lessonId, UUID sessionId) {
        progressRepository.markCompleted(learnerId, lessonId, sessionId, Instant.now());
    }

    @Transactional(readOnly = true)
    public CourseProgressView getCourseProgress(UUID learnerId, String courseId) {
        Course course = publishedCourse(courseId);
        List<Lesson> lessons = publishedLessons(course.getId());
        List<LessonProgressView> lessonViews = lessonProgressViews(learnerId, lessons);

        int completed = (int) lessonViews.stream().filter(view -> view.status().equals("COMPLETED")).count();
        Instant lastPracticedAt = lessonViews.stream()
                .map(LessonProgressView::lastPracticedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        return new CourseProgressView(
                course.getId(),
                course.getExamTrack().name(),
                course.getTitle(),
                course.getSummary(),
                lessons.size(),
                completed,
                rate(completed, lessons.size()),
                lastPracticedAt,
                lessonViews);
    }

    @Transactional(readOnly = true)
    public List<MyCourseView> getMyCourses(UUID learnerId) {
        List<LearnerLessonProgressRepository.ProgressRow> rows = progressRepository.findByLearner(learnerId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<String, Lesson> lessonsById = lessonRepository.findAllById(
                        rows.stream().map(LearnerLessonProgressRepository.ProgressRow::lessonId).toList())
                .stream()
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));

        Map<String, List<LearnerLessonProgressRepository.ProgressRow>> rowsByCourse = new LinkedHashMap<>();
        for (LearnerLessonProgressRepository.ProgressRow row : rows) {
            Lesson lesson = lessonsById.get(row.lessonId());
            if (lesson == null) {
                continue;
            }
            rowsByCourse.computeIfAbsent(lesson.getCourseId(), key -> new ArrayList<>()).add(row);
        }

        List<MyCourseView> result = new ArrayList<>();
        for (Map.Entry<String, List<LearnerLessonProgressRepository.ProgressRow>> entry : rowsByCourse.entrySet()) {
            Optional<Course> course = courseRepository.findById(entry.getKey())
                    .filter(found -> found.getStatus() == CourseStatus.PUBLISHED);
            if (course.isEmpty()) {
                continue;
            }

            List<LessonProgressView> lessonViews = lessonProgressViews(learnerId,
                    publishedLessons(course.get().getId()));
            if (lessonViews.isEmpty()) {
                continue;
            }

            int completed = (int) lessonViews.stream().filter(view -> view.status().equals("COMPLETED")).count();
            Instant lastPracticedAt = lessonViews.stream()
                    .map(LessonProgressView::lastPracticedAt)
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(null);

            Map<String, PracticeSession> openSessionsByLesson = sessionRepository.findOpenLessonSessions(learnerId)
                    .stream()
                    .collect(Collectors.toMap(PracticeSession::getLessonId, session -> session,
                            (first, second) -> first.getUpdatedAt().isAfter(second.getUpdatedAt()) ? first : second));

            LessonProgressView continueLesson = lessonViews.stream()
                    .filter(view -> view.continueSessionId() != null)
                    .max(Comparator.comparing(view -> openSessionsByLesson
                            .getOrDefault(view.lessonId(), null) == null
                            ? Instant.EPOCH
                            : openSessionsByLesson.get(view.lessonId()).getUpdatedAt()))
                    .orElse(null);

            LessonProgressView lastCompleted = lessonViews.stream()
                    .filter(view -> view.lastCompletedSessionId() != null)
                    .max(Comparator.comparing(LessonProgressView::lastPracticedAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);

            result.add(new MyCourseView(
                    course.get().getId(),
                    course.get().getExamTrack().name(),
                    course.get().getTitle(),
                    course.get().getSummary(),
                    lessonViews.size(),
                    completed,
                    rate(completed, lessonViews.size()),
                    lastPracticedAt,
                    continueLesson == null ? null : continueLesson.lessonId(),
                    continueLesson == null ? null : continueLesson.continueSessionId(),
                    lastCompleted == null ? null : lastCompleted.lessonId(),
                    lastCompleted == null ? null : lastCompleted.lastCompletedSessionId(),
                    lessonViews));
        }

        result.sort(Comparator.comparing(MyCourseView::lastPracticedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    /**
     * 课程详情使用的进度摘要；匿名访问返回全部未开始。
     */
    @Transactional(readOnly = true)
    public List<LessonProgressView> lessonProgressForCourse(Optional<UUID> learnerId, String courseId) {
        List<Lesson> lessons = publishedLessons(courseId);
        return lessonProgressViews(learnerId.orElse(null), lessons);
    }

    private List<LessonProgressView> lessonProgressViews(UUID learnerId, List<Lesson> lessons) {
        Map<String, LearnerLessonProgressRepository.ProgressRow> progress = learnerId == null
                ? Map.of()
                : progressRepository.findByLearnerAndLessonIds(
                        learnerId, lessons.stream().map(Lesson::getId).toList());

        Map<String, PracticeSession> openSessions = new HashMap<>();
        if (learnerId != null) {
            for (PracticeSession session : sessionRepository.findOpenLessonSessions(learnerId)) {
                openSessions.put(session.getLessonId(), session);
            }
        }

        List<LessonProgressView> views = new ArrayList<>();
        for (Lesson lesson : lessons) {
            LearnerLessonProgressRepository.ProgressRow row = progress.get(lesson.getId());
            PracticeSession openSession = openSessions.get(lesson.getId());
            String status = row != null && row.completed()
                    ? "COMPLETED"
                    : (row != null || openSession != null ? "IN_PROGRESS" : "NOT_STARTED");

            views.add(new LessonProgressView(
                    lesson.getId(),
                    lesson.getTitle(),
                    lesson.getSortOrder(),
                    questionCount(lesson),
                    status,
                    row == null ? null : row.lastActiveSessionId(),
                    row == null ? null : row.lastCompletedSessionId(),
                    openSession == null ? null : openSession.getId(),
                    row == null ? null : row.lastPracticedAt(),
                    row == null ? null : row.firstCompletedAt()));
        }
        return views;
    }

    private List<Lesson> publishedLessons(String courseId) {
        return lessonRepository.findByCourseIdOrderBySortOrderAsc(courseId).stream()
                .filter(lesson -> lesson.getPublishedVersionId() != null)
                .toList();
    }

    private long questionCount(Lesson lesson) {
        return lesson.getPublishedVersionId() == null
                ? 0
                : lessonVersionItemRepository.countByLessonVersionId(lesson.getPublishedVersionId());
    }

    private Course publishedCourse(String courseId) {
        return courseRepository.findById(courseId)
                .filter(course -> course.getStatus() == CourseStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("COURSE_NOT_FOUND", "课程不存在或尚未发布"));
    }

    private double rate(int completed, int total) {
        if (total == 0) {
            return 0;
        }
        return Math.round(completed * 10000.0 / total) / 10000.0;
    }
}
