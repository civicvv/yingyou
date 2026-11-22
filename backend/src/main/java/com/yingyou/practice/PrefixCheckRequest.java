package com.yingyou.practice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PrefixCheckRequest(
        @NotNull Long revision,
        @NotNull UUID itemId,
        @NotBlank String text
) {
}
