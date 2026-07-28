package com.expensewise.budget.repository;

import com.expensewise.budget.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserIdAndPeriodMonth(Long userId, LocalDate periodMonth);
}
