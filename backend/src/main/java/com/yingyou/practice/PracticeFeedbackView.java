package com.yingyou.practice;

import java.util.List;

public record PracticeFeedbackView(
        String answer,
        String explanationZh,
        List<ExplanationSegmentView> explanationSegments,
        List<String> grammarTags,
        Integer matchedCount,
        Integer firstMismatchIndex,
        String differenceType,
        String hint,
        NextPreviewView nextPreview
) {
}
