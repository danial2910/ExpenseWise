package com.expensewise.user.dto;

import java.time.Instant;
import java.time.LocalDate;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        boolean active,
        Instant createdAt,
        String phone,
        LocalDate dateOfBirth,
        String gender,
        String address,
        String avatarUrl
) {
}
