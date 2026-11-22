package com.yingyou.account;

import java.util.UUID;

public record RegisterResponse(
        UUID learnerId,
        boolean verificationRequired,
        String devVerificationToken
) {
}
