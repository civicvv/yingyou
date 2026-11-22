package com.yingyou.account;

import java.util.UUID;

public record LoginResponse(
        String kind,
        UUID learnerId,
        String nickname,
        String email,
        boolean guestRecordsMigrated,
        String csrfToken
) {
}
