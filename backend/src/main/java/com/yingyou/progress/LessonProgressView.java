package com.yingyou.progress;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record LessonProgressView(
        String lessonId,
        String title,
        int sortOrder,
        long questionCount,
        String status,
        UUID lastActiveSessionId,
        UUID lastCompletedSessionId,
        UUID continueSessionId,
        Instant lastPracticedAt,
        Instant firstCompletedAt
) {
}
