package com.expensewise.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the Jakarta Bean Validation constraints directly — no Spring
 * context needed, since these only fire through @Valid in the real
 * request path (covered end to end by the profile integration tests).
 */
class UpdateProfileRequestTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-03-15T10:00:00Z");
    private static final LocalDate FIXED_TODAY = LocalDate.ofInstant(FIXED_NOW, ZoneOffset.UTC);

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        // A fixed ClockProvider makes @PastOrPresent deterministic instead of
        // depending on the real system clock at whatever moment tests run.
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .clockProvider(() -> Clock.fixed(FIXED_NOW, ZoneOffset.UTC))
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void aFullyPopulatedRequestIsValid() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Sarah Lim", "+60 12-345 6789", LocalDate.of(1995, Month.JUNE, 1), "FEMALE", "1 Jalan Test, KL");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void blankOptionalFieldsAreValid() {
        UpdateProfileRequest request = new UpdateProfileRequest("Sarah Lim", "", null, "", "");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void aBlankFullNameIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest("   ", null, null, null, null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("fullName"));
    }

    @Test
    void aDateOfBirthInTheFutureIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest(
                "Sarah Lim", null, FIXED_TODAY.plusDays(1), null, null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("dateOfBirth"));
    }

    @Test
    void todayIsAValidDateOfBirth() {
        UpdateProfileRequest request = new UpdateProfileRequest("Sarah Lim", null, FIXED_TODAY, null, null);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void aGenderOutsideTheFixedSetIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest("Sarah Lim", null, null, "OTHER", null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("gender"));
    }

    @Test
    void eachAllowedGenderValueIsValid() {
        for (String gender : new String[]{"FEMALE", "MALE", "NON_BINARY", "SELF_DESCRIBED", "NOT_SPECIFIED"}) {
            UpdateProfileRequest request = new UpdateProfileRequest("Sarah Lim", null, null, gender, null);
            assertThat(validator.validate(request)).isEmpty();
        }
    }

    @Test
    void aMalformedPhoneNumberIsRejected() {
        UpdateProfileRequest request = new UpdateProfileRequest("Sarah Lim", "abc", null, null, null);

        Set<ConstraintViolation<UpdateProfileRequest>> violations = validator.validate(request);

        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("phone"));
    }
}
