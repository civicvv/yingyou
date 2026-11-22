package com.yingyou.catalog;

public record CourseSummaryResponse(
        String id,
        String examTrack,
        String title,
        String summary,
        long lessonCount
) {
}
