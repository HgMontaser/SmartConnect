package com.smartconnect.userservice.role;

import jakarta.validation.constraints.Size;

import java.util.List;

/** {@code permissions == null} leaves the role's permissions untouched. */
public record UpdateRoleRequest(
        @Size(max = 255) String description,
        List<String> permissions
) {
}
