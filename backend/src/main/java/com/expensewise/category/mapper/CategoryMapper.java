package com.expensewise.category.mapper;

import com.expensewise.category.dto.CategoryResponse;
import com.expensewise.category.entity.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryResponse toResponse(Category category);
}
