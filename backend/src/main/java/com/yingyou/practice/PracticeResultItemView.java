package com.yingyou.practice;

public record PracticeResultItemView(
        int position,
        String promptZh,
        String canonicalEn,
        String status,
        boolean hintUsed,
        boolean answerRevealed,
        int attemptCount,
        boolean firstTryCorrect
) {
}
