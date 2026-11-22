package com.yingyou.catalog;

import java.util.List;

public record CourseDetailResponse(
        String id,
        String examTrack,
        String title,
        String summary,
        List<LessonResponse> lessons
) {
}
