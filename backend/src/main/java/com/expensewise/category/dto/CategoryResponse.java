package com.expensewise.category.dto;

public record CategoryResponse(
        Long id,
        String name,
        String type,
        String icon,
        boolean system
) {
}
