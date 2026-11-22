package com.yingyou.practice;

import java.util.UUID;

public record PracticeCommandResponse(
        PracticeSessionView session,
        UUID attemptId
) {
}
