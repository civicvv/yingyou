package com.yingyou.personalization;

import com.yingyou.practice.PracticeSessionView;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PersonalizationViews {

    private PersonalizationViews() {
    }

    public record PreferencesView(String examTrack, String vocabBand, String autoDifficulty,
                                  String manualDifficulty, String effectiveDifficulty,
                                  long revision, Instant lastAssessmentAt) {
    }

    public record BatchView(PracticeSessionView session, String examTrack, String vocabBand,
                            String baselineDifficulty, int actualCount,
                            List<String> reasons, String fallbackReason) {
    }
}
