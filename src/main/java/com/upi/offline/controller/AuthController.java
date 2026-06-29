package com.upi.offline.controller;

import com.upi.offline.dto.*;
import com.upi.offline.entity.Role;
import com.upi.offline.entity.User;
import com.upi.offline.entity.RefreshToken;
import com.upi.offline.repository.UserRepository;
import com.upi.offline.security.JwtTokenProvider;
import com.upi.offline.service.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication APIs", description = "Endpoints for user registration, login, and token refresh")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthController(AuthenticationManager authenticationManager,
                          UserRepository userRepository,
                          PasswordEncoder encoder,
                          JwtTokenProvider jwtTokenProvider,
                          RefreshTokenService refreshTokenService) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.encoder = encoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Operation(summary = "Login user", description = "Authenticates user credentials and returns a JWT access token and a refresh token.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<JwtResponse>> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Authentication request received for user: {}", loginRequest.getUsername());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtTokenProvider.generateToken(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        User user = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user.getId());

        JwtResponse jwtResponse = new JwtResponse(jwt, refreshToken.getToken(), userDetails.getUsername(), roles);
        log.info("User {} authenticated successfully. Refresh token issued.", loginRequest.getUsername());

        return ResponseEntity.ok(ApiResponse.success("Authentication successful", jwtResponse));
    }

    @Operation(summary = "Register user", description = "Registers a new user with USER or ADMIN role.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(@Valid @RequestBody RegisterRequest signUpRequest) {
        log.info("Registration request received for username: {}", signUpRequest.getUsername());

        if (userRepository.existsByUsername(signUpRequest.getUsername())) {
            log.warn("Username {} is already taken", signUpRequest.getUsername());
            return ResponseEntity
                    .badRequest()
                    .body(new ApiResponse<>(false, "Username is already taken!"));
        }

        User user = new User();
        user.setUsername(signUpRequest.getUsername());
        user.setPassword(encoder.encode(signUpRequest.getPassword()));

        Set<Role> roles = new HashSet<>();
        String requestedRole = signUpRequest.getRole();

        if (requestedRole != null && requestedRole.equalsIgnoreCase("ADMIN")) {
            roles.add(Role.ROLE_ADMIN);
        } else {
            roles.add(Role.ROLE_USER);
        }

        user.setRoles(roles);
        userRepository.save(user);

        log.info("User registered successfully: username={}, roles={}", user.getUsername(), roles);
        return new ResponseEntity<>(ApiResponse.success("User registered successfully!"), HttpStatus.CREATED);
    }

    @Operation(summary = "Refresh access token", description = "Acquires a new JWT access token and refresh token rotation using a valid refresh token.")
    @PostMapping("/refreshtoken")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> refreshToken(@Valid @RequestBody TokenRefreshRequest request) {
        String requestRefreshToken = request.getRefreshToken();
        log.info("Refresh token request received");

        TokenRefreshResponse response = refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    List<String> roles = user.getRoles().stream()
                            .map(Role::name)
                            .collect(Collectors.toList());
                    String token = jwtTokenProvider.generateTokenFromUsername(user.getUsername(), roles);
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user.getId());
                    return new TokenRefreshResponse(token, newRefreshToken.getToken());
                })
                .orElseThrow(() -> {
                    log.error("Refresh token validation failed: token not found");
                    return new RuntimeException("Refresh token is not in database!");
                });

        log.info("Access token successfully refreshed");
        return ResponseEntity.ok(ApiResponse.success("Token refreshed successfully", response));
    }
}
