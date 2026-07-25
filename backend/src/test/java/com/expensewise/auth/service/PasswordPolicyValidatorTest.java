package com.expensewise.auth.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyValidatorTest {

    private final PasswordPolicyValidator validator = new PasswordPolicyValidator();

    @Test
    void acceptsAPasswordMeetingEveryRule() {
        assertThat(validator.isValid("Passw0rd")).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "short1A",       // too short (7 chars)
            "alllowercase1", // no uppercase
            "ALLUPPERCASE1", // no lowercase
            "NoDigitsHere",  // no digit
    })
    void rejectsPasswordsMissingARequiredRule(String password) {
        assertThat(validator.isValid(password)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(validator.isValid(null)).isFalse();
    }
}
