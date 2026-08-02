package com.expensewise.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateProfileRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be at most 255 characters")
        String fullName,

        // Empty string is treated the same as "not provided" (the frontend sends "" for
        // an untouched optional field, e.g. a blank input or the unselected <option value="">).
        @Pattern(regexp = "^$|^[0-9+\\-\\s()]{7,30}$", message = "Enter a valid phone number")
        String phone,

        @PastOrPresent(message = "Date of birth cannot be in the future")
        LocalDate dateOfBirth,

        @Pattern(regexp = "^$|FEMALE|MALE|NON_BINARY|SELF_DESCRIBED|NOT_SPECIFIED",
                message = "Gender must be one of FEMALE, MALE, NON_BINARY, SELF_DESCRIBED, NOT_SPECIFIED")
        String gender,

        @Size(max = 500, message = "Address must be at most 500 characters")
        String address
) {
}
