package com.expensewise.admin.dto;

import com.expensewise.entitlement.Feature;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record AdminCreateUserRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotNull(message = "Role is required")
        @Pattern(regexp = "USER|ADMIN", message = "Role must be USER or ADMIN")
        String role,

        // Null means "all features enabled" — the default for a new user.
        Set<Feature> enabledFeatures
) {
}
