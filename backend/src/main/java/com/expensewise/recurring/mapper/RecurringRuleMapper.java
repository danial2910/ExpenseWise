package com.expensewise.recurring.mapper;

import com.expensewise.category.entity.Category;
import com.expensewise.recurring.dto.RecurringRuleResponse;
import com.expensewise.recurring.entity.RecurringRule;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RecurringRuleMapper {

    @Mapping(target = "id", source = "rule.id")
    @Mapping(target = "type", source = "rule.type")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categoryIcon", source = "category.icon")
    @Mapping(target = "isActive", source = "rule.active")
    RecurringRuleResponse toResponse(RecurringRule rule, Category category);
}
