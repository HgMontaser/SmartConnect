package com.smartconnect.userservice.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code temporary} forces the user to pick a new password at next sign-in. */
public record ResetPasswordRequest(
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        boolean temporary
) {
}
