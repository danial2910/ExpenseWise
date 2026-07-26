package com.expensewise.category.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * All fields are optional — null means "leave unchanged". @Size/@Pattern
 * validators skip null values on their own, so an omitted field passes
 * validation while a present-but-blank one still fails it.
 */
public record PatchCategoryRequest(
        @Size(min = 1, max = 100, message = "Category name must be between 1 and 100 characters")
        String name,

        @Pattern(regexp = "INCOME|EXPENSE", message = "Type must be INCOME or EXPENSE")
        String type,

        @Size(max = 50, message = "Icon must be at most 50 characters")
        String icon
) {
}
