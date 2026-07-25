package com.expensewise.user.dto;

import java.time.Instant;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        boolean active,
        Instant createdAt
) {
}
