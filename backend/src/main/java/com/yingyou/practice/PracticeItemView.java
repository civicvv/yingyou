package com.yingyou.practice;

import java.util.UUID;

public record PracticeItemView(
        UUID id,
        String promptZh,
        String contextZh,
        String status,
        boolean hintUsed,
        boolean answerRevealed
) {
}
