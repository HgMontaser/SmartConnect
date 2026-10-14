package com.smartconnect.userservice.role;

import java.util.List;

public record RoleResponse(
        String name,
        String description,
        boolean system,
        List<String> permissions,
        int userCount
) {
}
