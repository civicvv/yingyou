package com.yingyou.account;

import java.time.Instant;
import java.util.UUID;

public record MeResponse(
        String kind,
        UUID learnerId,
        String nickname,
        String email,
        Boolean emailVerified,
        Instant createdAt
) {
}
