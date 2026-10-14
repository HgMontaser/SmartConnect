package com.smartconnect.userservice.permission;

import java.util.List;

public record PermissionResponse(
        String name,
        String description,
        boolean system,
        List<String> roles
) {
}
