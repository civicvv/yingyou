package com.yingyou.practice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PracticeCommandRequest(
        @NotNull UUID requestId,
        @NotNull Long revision,
        @NotNull UUID itemId,
        @NotBlank String action,
        String text,
        Integer wordIndex
) {
}
