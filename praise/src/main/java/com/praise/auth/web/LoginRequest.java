package com.praise.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record LoginRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-z0-9_]+$") String username,
        @NotBlank @Size(max = 72) String password) {

    public LoginRequest {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
