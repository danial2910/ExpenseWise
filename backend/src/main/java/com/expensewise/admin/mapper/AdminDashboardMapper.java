package com.expensewise.admin.mapper;

import com.expensewise.admin.dto.RecentSignupResponse;
import com.expensewise.user.entity.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminDashboardMapper {

    RecentSignupResponse toRecentSignupResponse(User user);
}
