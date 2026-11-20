package com.praise.auth.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50) @Pattern(regexp = "^[a-z0-9_]+$") String username,
        @NotBlank @Size(min = 8, max = 72) String password) {

    public RegisterRequest {
        username = username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }
}
