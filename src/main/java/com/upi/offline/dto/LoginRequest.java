package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body for user login")
public class LoginRequest {

    @Schema(description = "Username of the user", example = "user", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String username;

    @Schema(description = "Password of the user", example = "password", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
