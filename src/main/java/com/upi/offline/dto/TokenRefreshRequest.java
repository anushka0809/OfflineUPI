package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request body containing the refresh token to acquire a new access token")
public class TokenRefreshRequest {

    @Schema(description = "The Refresh Token value", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank
    private String refreshToken;

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
