package com.expensewise.user.mapper;

import com.expensewise.common.ActivityAction;
import com.expensewise.common.entity.ActivityLog;
import com.expensewise.user.dto.LoginHistoryResponse;
import com.expensewise.user.dto.UserResponse;
import com.expensewise.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "avatarUrl", ignore = true)
    UserResponse toResponse(User user);

    @Mapping(target = "avatarUrl", source = "avatarUrl")
    UserResponse toResponse(User user, String avatarUrl);

    @Mapping(target = "occurredAt", source = "createdAt")
    @Mapping(target = "status", source = "action", qualifiedByName = "mapLoginStatus")
    LoginHistoryResponse toLoginHistoryResponse(ActivityLog log);

    // @Named so MapStruct only applies this where explicitly requested above —
    // otherwise it silently applies to every String->String field in this
    // mapper (including toResponse's email/fullName/phone/etc), since a
    // single-arg default method with matching types is picked up implicitly.
    @Named("mapLoginStatus")
    default String mapLoginStatus(String action) {
        return ActivityAction.LOGIN_SUCCESS.equals(action) ? "Success" : "Failed";
    }
}
