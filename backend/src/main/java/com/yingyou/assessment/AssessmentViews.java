package com.yingyou.assessment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AssessmentViews {

    private AssessmentViews() {
    }

    public record OptionView(String id, String label) {
    }

    public record ItemView(String id, int position, String word, String promptZh,
                           String contextEn, List<OptionView> options) {
    }

    public record SessionView(UUID id, String status, int position, int total, long revision,
                              int answeredCount, ItemView currentItem) {
    }

    public record BandStatView(int band, int correct, int unknown, int total, double correctedRatio) {
    }

    public record ResultView(UUID sessionId, String vocabBand, Integer estimatedFamilies,
                             String qualitySignal, boolean atCeiling, boolean unstable,
                             String algorithmVersion, Instant completedAt,
                             List<BandStatView> bandStats) {
    }

    public record HistoryView(UUID sessionId, String vocabBand, String qualitySignal, Instant completedAt) {
    }
}
