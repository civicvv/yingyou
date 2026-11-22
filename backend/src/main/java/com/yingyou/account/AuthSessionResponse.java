package com.yingyou.account;

import java.util.UUID;

/**
 * kind 取值为 ANONYMOUS、GUEST 或 ACCOUNT。
 */
public record AuthSessionResponse(String kind, UUID learnerId, String csrfToken) {
}
