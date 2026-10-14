package com.smartconnect.userservice.permission;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9-]{0,29}:[a-z][a-z0-9-]{0,29}$",
                message = "must look like resource:action, e.g. reports:export (lowercase letters, digits, '-')")
        String name,
        @Size(max = 255) String description
) {
}
