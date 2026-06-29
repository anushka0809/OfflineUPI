package com.upi.offline.service;

import com.upi.offline.entity.RefreshToken;
import com.upi.offline.repository.RefreshTokenRepository;
import com.upi.offline.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    @Value("${offlineupi.app.jwtRefreshExpirationMs:86400000}")
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.userRepository = userRepository;
    }

    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Transactional
    public RefreshToken createRefreshToken(Long userId) {
        log.info("Creating refresh token for user ID: {}", userId);
        
        // Remove existing refresh token if present
        userRepository.findById(userId).ifPresent(user -> {
            refreshTokenRepository.deleteByUser(user);
            refreshTokenRepository.flush();
        });

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(userRepository.findById(userId).orElseThrow(() -> new RuntimeException("User not found")));
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));
        refreshToken.setToken(UUID.randomUUID().toString());

        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        log.info("Refresh token created successfully for user ID: {}", userId);
        return saved;
    }

    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            log.warn("Refresh token was expired for user: {}", token.getUser().getUsername());
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new signin request");
        }
        return token;
    }

    @Transactional
    public int deleteByUserId(Long userId) {
        log.info("Deleting refresh tokens for user ID: {}", userId);
        return userRepository.findById(userId)
                .map(user -> refreshTokenRepository.deleteByUser(user))
                .orElse(0);
    }
}
