package com.smartconnect.userservice.permission;

import jakarta.validation.constraints.Size;

public record UpdatePermissionRequest(
        @Size(max = 255) String description
) {
}
