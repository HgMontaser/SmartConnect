package com.smartconnect.userservice.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank @Email String email,
        String firstName,
        String lastName,
        @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password,
        List<String> realmRoles
) {
}
