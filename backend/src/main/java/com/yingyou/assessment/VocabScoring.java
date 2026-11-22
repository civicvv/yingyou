package com.yingyou.assessment;

import com.yingyou.assessment.AssessmentViews.BandStatView;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VocabScoring {

    public static final String VERSION = "v1";

    public record Score(String vocabBand, Integer estimatedFamilies, String qualitySignal,
                        boolean atCeiling, boolean unstable, List<BandStatView> bandStats) {
    }

    public Score score(int[] correctByBand, int[] unknownByBand, int itemsPerBand) {
        List<BandStatView> stats = new ArrayList<>();
        int estimated = 0;
        int totalCorrect = 0;
        for (int band = 1; band <= 6; band++) {
            int correct = correctByBand[band];
            int unknown = unknownByBand[band];
            totalCorrect += correct;
            double corrected = Math.max(0, Math.min(1,
                    (correct - (itemsPerBand - (double) unknown) * 0.25) / (itemsPerBand * 0.75)));
            estimated += (int) Math.round(corrected * 1000);
            stats.add(new BandStatView(band, correct, unknown, itemsPerBand,
                    Math.round(corrected * 10000.0) / 10000.0));
        }

        String vocabBand = band(estimated);
        boolean ceiling = correctByBand[6] >= 5 && totalCorrect >= 33;
        boolean unstable = isUnstable(correctByBand);
        String signal = unstable ? "UNSTABLE" : (ceiling ? "AT_CEILING" : "STANDARD");
        if (unstable && ceiling) {
            vocabBand = downgrade(vocabBand);
        }
        return new Score(vocabBand, estimated, signal, ceiling, unstable, stats);
    }

    private String band(int estimated) {
        if (estimated < 1500) {
            return "V1";
        }
        if (estimated < 3000) {
            return "V2";
        }
        if (estimated < 4500) {
            return "V3";
        }
        return "V4";
    }

    private String downgrade(String band) {
        return switch (band) {
            case "V4" -> "V3";
            case "V3" -> "V2";
            case "V2" -> "V1";
            default -> "V1";
        };
    }

    private boolean isUnstable(int[] correctByBand) {
        for (int higher = 2; higher <= 6; higher++) {
            for (int lower = 1; lower < higher; lower++) {
                if (correctByBand[higher] <= correctByBand[lower] - 3) {
                    return true;
                }
            }
        }
        return false;
    }
}
