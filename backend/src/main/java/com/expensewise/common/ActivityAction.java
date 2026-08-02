package com.expensewise.common;

/**
 * Canonical activity_logs.action values used across the auth module.
 */
public final class ActivityAction {

    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String ADMIN_USER_ENABLED = "ADMIN_USER_ENABLED";
    public static final String ADMIN_USER_DISABLED = "ADMIN_USER_DISABLED";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";
    public static final String AVATAR_UPDATED = "AVATAR_UPDATED";
    public static final String AVATAR_REMOVED = "AVATAR_REMOVED";
    public static final String LOGOUT_OTHERS = "LOGOUT_OTHERS";

    private ActivityAction() {
    }
}
