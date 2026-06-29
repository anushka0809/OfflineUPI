package com.upi.offline.config;

import com.upi.offline.entity.Role;
import com.upi.offline.entity.User;
import com.upi.offline.repository.UserRepository;
import com.upi.offline.service.WalletService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;

    public DatabaseInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder,
                               WalletService walletService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletService = walletService;
    }

    @Override
    public void run(String... args) {
        log.info("DatabaseInitializer: seeding default users");

        createUserIfMissing("user", "password", Role.ROLE_USER, 10000.0);
        createUserIfMissing("admin", "password", Role.ROLE_ADMIN, 50000.0);
        createUserIfMissing("alice", "password", Role.ROLE_USER, 15000.0);
        createUserIfMissing("bob", "password", Role.ROLE_USER, 12000.0);
        createUserIfMissing("charlie", "password", Role.ROLE_USER, 8000.0);

        userRepository.findAll().forEach(user -> {
            boolean updated = false;
            if (user.getBalance() == null || user.getBalance() <= 0) {
                double defaultBalance = "admin".equals(user.getUsername()) ? 50000.0 : 10000.0;
                user.setBalance(defaultBalance);
                updated = true;
            }
            if (user.getUpiId() == null) {
                user.setUpiId(user.getUsername() + "@offlineupi");
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
            }
        });
    }

    private void createUserIfMissing(String username, String password, Role role, double balance) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setBalance(balance);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        user.setRoles(roles);
        walletService.initializeNewUser(user);
        userRepository.save(user);
        log.info("Created default user '{}' with balance ₹{}", username, balance);
    }
}
