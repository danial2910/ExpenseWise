package com.expensewise.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
