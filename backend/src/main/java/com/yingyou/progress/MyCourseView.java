package com.yingyou.progress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MyCourseView(
        String courseId,
        String examTrack,
        String title,
        String summary,
        int lessonCount,
        int completedLessonCount,
        double completionRate,
        Instant lastPracticedAt,
        String continueLessonId,
        UUID continueSessionId,
        String lastCompletedLessonId,
        UUID lastCompletedSessionId,
        List<LessonProgressView> lessons
) {
}
