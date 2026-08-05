package com.expensewise.config;

import jakarta.validation.ClockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Clock;

/**
 * A single injectable Clock, so "today" can be swapped for a fixed instant
 * in tests instead of every caller reaching for Clock.systemUTC() (or
 * LocalDate.now()) directly. First real use: BudgetService's Asia/Kuala_Lumpur
 * "this month" default (CLAUDE.md's Time rule).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Jakarta Bean Validation's @Past/@Future/@PastOrPresent annotations
     * (e.g. UpdateProfileRequest.dateOfBirth) read "now" from a ClockProvider
     * that, unless overridden, defaults to Clock.systemDefaultZone() — the
     * JVM's local timezone, not this app's own UTC clock() bean above. On a
     * machine whose local zone runs ahead of UTC (e.g. Asia/Kuala_Lumpur,
     * UTC+8), that mismatch let a date exactly one day in the future (by
     * LocalDate.now(clock)) validate as "present" instead of "future" for a
     * few hours around each UTC day boundary — found via
     * ProfileIntegrationTest failing only at certain times of day. Binding
     * ClockProvider to the same clock() bean makes validation and the rest
     * of the app agree on "now".
     */
    @Bean
    public ClockProvider clockProvider(Clock clock) {
        return () -> clock;
    }

    /**
     * A plain ClockProvider bean isn't enough on its own — Spring Boot's
     * autoconfigured Validator (ValidationAutoConfiguration) doesn't look
     * one up automatically; it has to be wired into the LocalValidatorFactoryBean
     * explicitly. LocalValidatorFactoryBean has no setClockProvider(...)
     * setter — setConfigurationInitializer(...) is the supported hook for
     * customizing the underlying jakarta.validation Configuration before the
     * ValidatorFactory is built. Naming this bean "defaultValidator" (Boot's
     * own bean name for its autoconfigured one) makes Boot's
     * @ConditionalOnMissingBean back off cleanly rather than defining a
     * second, competing Validator.
     */
    @Bean
    public LocalValidatorFactoryBean defaultValidator(ClockProvider clockProvider) {
        LocalValidatorFactoryBean factoryBean = new LocalValidatorFactoryBean();
        factoryBean.setConfigurationInitializer(configuration -> configuration.clockProvider(clockProvider));
        return factoryBean;
    }
}
