package com.smartconnect.userservice.permission;

import java.util.List;

public record PermissionResponse(
        String name,
        String description,
        boolean system,
        /** Service that defines this permission, or null for one created in the backoffice. */
        String owner,
        boolean orphaned,
        List<String> roles
) {
}
