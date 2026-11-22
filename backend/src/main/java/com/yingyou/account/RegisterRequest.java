package com.yingyou.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 160) String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotBlank @Size(max = 40) String nickname,
        @NotBlank @Size(max = 32) String termsVersion
) {
}
