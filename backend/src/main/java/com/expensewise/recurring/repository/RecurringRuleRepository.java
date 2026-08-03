package com.expensewise.recurring.repository;

import com.expensewise.recurring.entity.RecurringRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface RecurringRuleRepository extends JpaRepository<RecurringRule, Long> {

    Page<RecurringRule> findByUserId(Long userId, Pageable pageable);

    List<RecurringRule> findByActiveTrueAndNextDueDateLessThanEqual(LocalDate date);

    List<RecurringRule> findByUserIdAndActiveTrueAndNextDueDateLessThanEqual(Long userId, LocalDate date);
}
