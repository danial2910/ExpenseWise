package com.expensewise.entitlement;

/**
 * A gate-able feature area. Stored as its {@code name()} string in
 * {@code user_feature_entitlements.feature}, never an ordinal. Profile is
 * deliberately absent — it is always available and never gated.
 */
public enum Feature {
    TRANSACTIONS,
    CATEGORIES,
    BUDGETS,
    REPORTS,
    AI_ASSISTANT,
    NEWS
}
