package com.yingyou.practice;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 判分规则 v1：NFKC、弯撇号归一、空白压缩、小写、去句末标点。
 */
@Service
public class GradingService {

    public static final String VERSION = "v1";
    private static final int MAX_CODE_POINTS = 500;

    public record GradeResult(
            String normalizedText,
            boolean correct,
            Integer matchedCount,
            Integer firstMismatchIndex,
            String differenceType
    ) {
    }

    public record PrefixResult(int matchedCount, Integer firstMismatchIndex) {
    }

    public boolean isTooLong(String input) {
        return input.codePointCount(0, input.length()) > MAX_CODE_POINTS;
    }

    public String normalize(String input) {
        String value = Normalizer.normalize(input, Normalizer.Form.NFKC);
        value = value.replace('\u2018', '\'').replace('\u2019', '\'');
        value = value.replaceAll("\\s+", " ").trim();
        value = value.toLowerCase(Locale.ROOT);
        value = value.replaceAll("[.!?]+\\s*$", "");
        return value.trim();
    }

    public GradeResult grade(String rawText, String canonical, List<String> variants) {
        String normalizedInput = normalize(rawText);
        List<String> candidates = candidates(canonical, variants);

        for (String candidate : candidates) {
            if (normalizedInput.equals(normalize(candidate))) {
                return new GradeResult(normalizedInput, true, wordCount(normalizedInput), null, null);
            }
        }

        String[] inputWords = words(normalizedInput);
        Alignment best = null;
        for (String candidate : candidates) {
            Alignment alignment = align(inputWords, words(normalize(candidate)));
            if (best == null || alignment.distance < best.distance) {
                best = alignment;
            }
        }

        if (best == null) {
            return new GradeResult(normalizedInput, false, 0, 0, "MISSPELLED");
        }
        return new GradeResult(normalizedInput, false, best.matchedCount, best.firstMismatchIndex,
                best.differenceType);
    }

    public PrefixResult checkPrefix(String prefixText, String canonical, List<String> variants) {
        String[] prefixWords = words(normalize(prefixText));
        int bestMatched = 0;

        for (String candidate : candidates(canonical, variants)) {
            String[] candidateWords = words(normalize(candidate));
            int matched = 0;
            while (matched < prefixWords.length && matched < candidateWords.length
                    && candidateWords[matched].equals(prefixWords[matched])) {
                matched++;
            }
            if (matched > bestMatched) {
                bestMatched = matched;
            }
        }

        Integer firstMismatchIndex = bestMatched < prefixWords.length ? bestMatched : null;
        return new PrefixResult(bestMatched, firstMismatchIndex);
    }

    private List<String> candidates(String canonical, List<String> variants) {
        List<String> candidates = new ArrayList<>();
        candidates.add(canonical);
        if (variants != null) {
            for (String variant : variants) {
                if (variant != null && !variant.isBlank()) {
                    candidates.add(variant);
                }
            }
        }
        return candidates;
    }

    private String[] words(String normalized) {
        return normalized.isEmpty() ? new String[0] : normalized.split(" ");
    }

    private int wordCount(String normalized) {
        return words(normalized).length;
    }

    private Alignment align(String[] input, String[] target) {
        int n = input.length;
        int m = target.length;
        int[][] distance = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= m; j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int substitution = distance[i - 1][j - 1] + (input[i - 1].equals(target[j - 1]) ? 0 : 1);
                int extra = distance[i - 1][j] + 1;
                int missing = distance[i][j - 1] + 1;
                distance[i][j] = Math.min(substitution, Math.min(extra, missing));
            }
        }

        List<Operation> operations = new ArrayList<>();
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && input[i - 1].equals(target[j - 1])
                    && distance[i][j] == distance[i - 1][j - 1]) {
                operations.add(Operation.MATCH);
                i--;
                j--;
            } else if (i > 0 && j > 0 && distance[i][j] == distance[i - 1][j - 1] + 1) {
                operations.add(Operation.SUBSTITUTE);
                i--;
                j--;
            } else if (i > 0 && distance[i][j] == distance[i - 1][j] + 1) {
                operations.add(Operation.EXTRA);
                i--;
            } else {
                operations.add(Operation.MISSING);
                j--;
            }
        }
        java.util.Collections.reverse(operations);

        int matchedCount = 0;
        for (Operation operation : operations) {
            if (operation == Operation.MATCH) {
                matchedCount++;
                continue;
            }
            return new Alignment(distance[n][m], matchedCount, matchedCount, differenceType(operation));
        }
        return new Alignment(distance[n][m], matchedCount, null, null);
    }

    private String differenceType(Operation operation) {
        return switch (operation) {
            case SUBSTITUTE -> "MISSPELLED";
            case EXTRA -> "EXTRA";
            case MISSING -> "MISSING";
            case MATCH -> null;
        };
    }

    private enum Operation {
        MATCH,
        SUBSTITUTE,
        EXTRA,
        MISSING
    }

    private record Alignment(int distance, int matchedCount, Integer firstMismatchIndex, String differenceType) {
    }
}
