package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response payload returned after a successful token refresh operation")
public class TokenRefreshResponse {

    @Schema(description = "Newly generated JWT Access Token")
    private String accessToken;

    @Schema(description = "The Refresh Token value")
    private String refreshToken;

    @Schema(description = "Token Type Prefix", example = "Bearer")
    private String tokenType = "Bearer";

    public TokenRefreshResponse(String accessToken, String refreshToken) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }
}
