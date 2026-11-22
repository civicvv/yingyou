package com.yingyou.progress;

import java.time.Instant;
import java.util.List;

public record CourseProgressView(
        String courseId,
        String examTrack,
        String title,
        String summary,
        int lessonCount,
        int completedLessonCount,
        double completionRate,
        Instant lastPracticedAt,
        List<LessonProgressView> lessons
) {
}
