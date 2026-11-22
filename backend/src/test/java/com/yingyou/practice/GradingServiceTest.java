package com.yingyou.practice;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingServiceTest {

    private final GradingService gradingService = new GradingService();

    @Test
    void normalizeHandlesCurlyApostropheWhitespaceCaseAndTrailingPunctuation() {
        assertEquals("i can't wait", gradingService.normalize("  I   can’t  wait.  "));
    }

    @Test
    void gradeAcceptsCanonicalAndAnswerVariants() {
        String canonical = "Please hand in your homework";
        List<String> variants = List.of("Please submit your homework");

        assertTrue(gradingService.grade("please hand in your homework.", canonical, variants).correct());
        assertTrue(gradingService.grade("Please submit your homework", canonical, variants).correct());
    }

    @Test
    void gradeReportsFirstMismatchForWrongAnswer() {
        GradingService.GradeResult result = gradingService.grade(
                "I go to the library to study at nine every morning",
                "I go to the library to study at eight every morning",
                List.of());

        assertFalse(result.correct());
        assertEquals(8, result.matchedCount());
        assertEquals("MISSPELLED", result.differenceType());
    }

    @Test
    void prefixCheckUsesVariants() {
        GradingService.PrefixResult prefix = gradingService.checkPrefix(
                "Please submit",
                "Please hand in your homework",
                List.of("Please submit your homework"));

        assertEquals(2, prefix.matchedCount());
        assertEquals(null, prefix.firstMismatchIndex());
    }
}
