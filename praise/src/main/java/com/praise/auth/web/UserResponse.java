package com.praise.auth.web;

import com.praise.user.domain.UserAccount;

public record UserResponse(Long id, String username) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(user.getId(), user.getUsername());
    }
}
