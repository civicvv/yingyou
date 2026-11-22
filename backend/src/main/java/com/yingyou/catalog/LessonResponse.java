package com.yingyou.catalog;

import java.util.UUID;

public record LessonResponse(
        String id,
        String title,
        int sortOrder,
        long questionCount,
        String progressStatus,
        UUID lastActiveSessionId,
        UUID lastCompletedSessionId,
        UUID continueSessionId
) {
}
