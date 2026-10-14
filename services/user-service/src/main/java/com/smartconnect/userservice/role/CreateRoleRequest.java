package com.smartconnect.userservice.role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "^[a-z][a-z0-9_-]{1,39}$",
                message = "must be 2-40 chars: lowercase letters, digits, '-' or '_', starting with a letter")
        String name,
        @Size(max = 255) String description,
        List<String> permissions
) {
}
