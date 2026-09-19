package com.smartconnect.userservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record UpdateUserRequest(
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        List<String> realmRoles
) {
}
