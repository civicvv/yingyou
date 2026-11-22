package com.yingyou.practice;

import java.util.List;
import java.util.UUID;

public record PracticeResultView(
        UUID sessionId,
        String courseId,
        String lessonId,
        String lessonVersionId,
        String lessonTitle,
        int total,
        int independentCount,
        int assistedCount,
        int skippedCount,
        int firstTryCorrectCount,
        double firstTryAccuracy,
        List<PracticeResultItemView> items
) {
}
