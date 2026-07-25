package com.expensewise.auth.dto;

import com.expensewise.user.dto.UserResponse;

/**
 * The refresh token is never included here — it's set as an httpOnly
 * cookie by the controller, not returned in the JSON body.
 */
public record LoginResponse(
        String accessToken,
        UserResponse user
) {
}
