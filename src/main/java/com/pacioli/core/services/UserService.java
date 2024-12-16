package com.pacioli.core.services;

import com.pacioli.core.DTO.UpdateUserInfoRequest;
import com.pacioli.core.DTO.UserInfo;
import com.pacioli.core.models.User;

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserInfo createUser(UserInfo userInfo);

    UserInfo assignRolesToUser(String userId, List<String> roleIds);

    List<UserInfo> getAllUsers();

    User assignRoleToUser(String userId, String roleId);

    User removeRoleFromUser(String userId, String roleId);

    List<User> getUsersByCabinetId(Long cabinetId);

    void updateUserHoldStatus(UUID userId, boolean isHold);
    void updateUserDeleteStatus(UUID userId, boolean isDeleted);

    void updateUserPassword(UUID userId, String newPassword);

    void updateUserInfo(UUID userId, UpdateUserInfoRequest request);

}
