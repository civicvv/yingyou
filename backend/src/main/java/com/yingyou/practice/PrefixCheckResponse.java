package com.yingyou.practice;

import java.util.UUID;

public record PrefixCheckResponse(
        long revision,
        UUID itemId,
        int matchedCount,
        Integer firstMismatchIndex
) {
}
