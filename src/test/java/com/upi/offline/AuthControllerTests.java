package com.upi.offline;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.upi.offline.dto.*;
import com.upi.offline.entity.User;
import com.upi.offline.repository.UserRepository;
import com.upi.offline.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RefreshTokenService refreshTokenService;

    private String testUsername;

    @BeforeEach
    void setUp() {
        testUsername = "testuser_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void testRegisterUserSuccess() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(testUsername);
        request.setPassword("password123");
        request.setRole("USER");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User registered successfully!"));

        assertTrue(userRepository.existsByUsername(testUsername));
        User user = userRepository.findByUsername(testUsername).orElseThrow();
        assertTrue(passwordEncoder.matches("password123", user.getPassword()));
    }

    @Test
    void testRegisterDuplicateUsernameReturnsBadRequest() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("user"); // default user exists from DatabaseInitializer
        request.setPassword("password");
        request.setRole("USER");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username is already taken!"));
    }

    @Test
    void testLoginSuccessAndIssueTokens() throws Exception {
        // Register user first
        User user = new User();
        user.setUsername(testUsername);
        user.setPassword(passwordEncoder.encode("password123"));
        userRepository.save(user);

        LoginRequest request = new LoginRequest();
        request.setUsername(testUsername);
        request.setPassword("password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value(testUsername));
    }

    @Test
    void testLoginInvalidCredentialsReturnsUnauthorized() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent_user");
        request.setPassword("wrong_password");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testRefreshTokenRotationSuccess() throws Exception {
        // Register user first
        User user = new User();
        user.setUsername(testUsername);
        user.setPassword(passwordEncoder.encode("password123"));
        user = userRepository.save(user);

        // Perform login to acquire initial tokens
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(testUsername);
        loginRequest.setPassword("password123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseString = loginResult.getResponse().getContentAsString();
        ApiResponse<?> apiResponse = objectMapper.readValue(responseString, ApiResponse.class);
        
        // Deserialize inner JwtResponse
        JwtResponse jwtResponse = objectMapper.convertValue(apiResponse.getData(), JwtResponse.class);
        String initialRefreshToken = jwtResponse.getRefreshToken();
        assertNotNull(initialRefreshToken);

        // Perform refresh request
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(initialRefreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String refreshResponseString = refreshResult.getResponse().getContentAsString();
        ApiResponse<?> refreshApiResponse = objectMapper.readValue(refreshResponseString, ApiResponse.class);
        TokenRefreshResponse refreshResponse = objectMapper.convertValue(refreshApiResponse.getData(), TokenRefreshResponse.class);
        
        String rotatedRefreshToken = refreshResponse.getRefreshToken();
        assertNotNull(rotatedRefreshToken);
        assertNotEquals(initialRefreshToken, rotatedRefreshToken); // verify rotation

        // Verify the old token is now invalidated/deleted
        assertTrue(refreshTokenService.findByToken(initialRefreshToken).isEmpty());
        assertTrue(refreshTokenService.findByToken(rotatedRefreshToken).isPresent());
    }

    @Test
    void testRefreshTokenInvalidTokenReturnsError() throws Exception {
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken("invalid-uuid-token");

        mockMvc.perform(post("/api/auth/refreshtoken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Refresh token is not in database!"));
    }
}
