package com.expensewise.transaction.mapper;

import com.expensewise.category.entity.Category;
import com.expensewise.transaction.dto.TransactionResponse;
import com.expensewise.transaction.entity.Transaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    @Mapping(target = "id", source = "transaction.id")
    @Mapping(target = "type", source = "transaction.type")
    @Mapping(target = "categoryName", source = "category.name")
    @Mapping(target = "categoryIcon", source = "category.icon")
    TransactionResponse toResponse(Transaction transaction, Category category);
}
