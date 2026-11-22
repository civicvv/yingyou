package com.yingyou.practice;

import java.util.UUID;

public record PracticeSessionView(
        UUID id,
        String status,
        long revision,
        int position,
        int total,
        String courseId,
        String lessonId,
        String lessonTitle,
        PracticeItemView currentItem,
        PracticeFeedbackView feedback
) {
}
