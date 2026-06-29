package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request body for user registration")
public class RegisterRequest {

    @Schema(description = "Desired username", example = "newuser", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(min = 3, max = 20)
    private String username;

    @Schema(description = "Desired password", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    @Size(min = 8, max = 40)
    private String password;

    @Schema(description = "Optional role name (USER or ADMIN)", example = "USER")
    private String role;

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
