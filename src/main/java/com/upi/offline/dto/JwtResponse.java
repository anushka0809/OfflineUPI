package com.upi.offline.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Response returned after successful authentication containing JWT token and refresh token")
public class JwtResponse {

    @Schema(description = "JWT Token")
    private String token;

    @Schema(description = "Prefix for HTTP Authorization Header", example = "Bearer")
    private String type = "Bearer";

    @Schema(description = "The Refresh Token value")
    private String refreshToken;

    @Schema(description = "Username of the authenticated user")
    private String username;

    @Schema(description = "List of user roles")
    private List<String> roles;

    public JwtResponse(String accessToken, String refreshToken, String username, List<String> roles) {
        this.token = accessToken;
        this.refreshToken = refreshToken;
        this.username = username;
        this.roles = roles;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public List<String> getRoles() {
        return roles;
    }

    public void setRoles(List<String> roles) {
        this.roles = roles;
    }
}
