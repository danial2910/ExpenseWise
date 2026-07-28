package com.expensewise.budget.mapper;

import com.expensewise.budget.dto.BudgetResponse;
import com.expensewise.budget.entity.Budget;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface BudgetMapper {

    @Mapping(target = "id", source = "budget.id")
    @Mapping(target = "categoryId", source = "budget.categoryId")
    @Mapping(target = "amount", source = "budget.amount")
    @Mapping(target = "periodMonth", source = "budget.periodMonth")
    @Mapping(target = "createdAt", source = "budget.createdAt")
    BudgetResponse toResponse(Budget budget, String categoryName, BigDecimal spent, BigDecimal remaining,
                              BigDecimal progressPercent, boolean exceeded);
}
