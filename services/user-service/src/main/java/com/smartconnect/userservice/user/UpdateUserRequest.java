package com.smartconnect.userservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** {@code realmRoles == null} / {@code enabled == null} leave those fields untouched. */
public record UpdateUserRequest(
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        Boolean enabled,
        List<String> realmRoles
) {
}
