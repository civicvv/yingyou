package com.yingyou.assessment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VocabScoringTest {

    private final VocabScoring scoring = new VocabScoring();

    @Test
    void sampleSequenceMatchesDocumentedEstimateAndBand() {
        int[] correct = {0, 6, 5, 4, 3, 2, 1};
        int[] unknown = new int[7];

        VocabScoring.Score score = scoring.score(correct, unknown, 6);

        assertEquals(2778, score.estimatedFamilies());
        assertEquals("V2", score.vocabBand());
        assertEquals("UNSTABLE", score.qualitySignal());
    }

    @Test
    void allUnknownProducesLowestBand() {
        int[] correct = new int[7];
        int[] unknown = {0, 6, 6, 6, 6, 6, 6};

        VocabScoring.Score score = scoring.score(correct, unknown, 6);

        assertEquals(0, score.estimatedFamilies());
        assertEquals("V1", score.vocabBand());
    }

    @Test
    void topBandAndTotalTriggerCeiling() {
        int[] correct = {0, 6, 6, 6, 6, 6, 5};
        int[] unknown = new int[7];

        VocabScoring.Score score = scoring.score(correct, unknown, 6);

        assertEquals("V4", score.vocabBand());
        assertTrue(score.atCeiling());
        assertEquals("AT_CEILING", score.qualitySignal());
        assertFalse(score.unstable());
    }

    @Test
    void unstableSignalDowngradesBand() {
        int[] correct = {0, 6, 3, 3, 3, 3, 3};
        int[] unknown = new int[7];

        VocabScoring.Score score = scoring.score(correct, unknown, 6);

        assertTrue(score.unstable());
        assertEquals("UNSTABLE", score.qualitySignal());
        assertEquals("V2", score.vocabBand());
    }
}
