package com.yingyou.practice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePracticeSessionRequest(
        @NotBlank String lessonId,
        @NotNull UUID requestId
) {
}
