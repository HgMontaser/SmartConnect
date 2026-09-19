package com.smartconnect.userservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        @NotBlank String password,
        List<String> realmRoles
) {
}
