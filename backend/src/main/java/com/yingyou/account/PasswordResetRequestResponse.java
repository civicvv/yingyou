package com.yingyou.account;

public record PasswordResetRequestResponse(
        boolean accepted,
        String devResetToken
) {
}
