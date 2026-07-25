package com.expensewise.user.mapper;

import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(User user);
}
