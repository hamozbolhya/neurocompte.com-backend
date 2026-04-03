package com.pacioli.core.services;

import com.pacioli.core.DTO.UpdateUserInfoRequest;
import com.pacioli.core.DTO.UserInfo;
import com.pacioli.core.models.User;
import org.springframework.lang.NonNull;

import java.util.List;
import java.util.UUID;

public interface UserService {
    User getCurrentUser();

    UserInfo createUser(UserInfo userInfo);

    UserInfo assignRolesToUser(String userId, @NonNull List<String> roleIds);

    List<UserInfo> getAllUsers();

    User assignRoleToUser(String userId, @NonNull String roleId);

    User removeRoleFromUser(String userId, @NonNull String roleId);

    List<User> getUsersByCabinetId(@NonNull Long cabinetId);

    void updateUserHoldStatus(@NonNull UUID userId, boolean isHold);
    void updateUserDeleteStatus(@NonNull UUID userId, boolean isDeleted);

    void updateUserPassword(@NonNull UUID userId, String newPassword);

    void updateUserInfo(@NonNull UUID userId, UpdateUserInfoRequest request);

}
