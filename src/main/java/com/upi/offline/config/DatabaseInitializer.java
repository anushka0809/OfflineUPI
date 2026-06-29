package com.upi.offline.config;

import com.upi.offline.entity.Role;
import com.upi.offline.entity.User;
import com.upi.offline.repository.UserRepository;
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

    public DatabaseInitializer(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("DatabaseInitializer: Checking if default users exist");

        if (!userRepository.findByUsername("user").isPresent()) {
            User user = new User();
            user.setUsername("user");
            user.setPassword(passwordEncoder.encode("password"));
            Set<Role> roles = new HashSet<>();
            roles.add(Role.ROLE_USER);
            user.setRoles(roles);
            userRepository.save(user);
            log.info("DatabaseInitializer: Default USER 'user' created with password 'password'");
        }

        if (!userRepository.findByUsername("admin").isPresent()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("password"));
            Set<Role> roles = new HashSet<>();
            roles.add(Role.ROLE_ADMIN);
            admin.setRoles(roles);
            userRepository.save(admin);
            log.info("DatabaseInitializer: Default ADMIN 'admin' created with password 'password'");
        }
    }
}
