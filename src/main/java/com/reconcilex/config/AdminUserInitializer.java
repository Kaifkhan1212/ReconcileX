package com.reconcilex.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.entity.AppUser;
import com.reconcilex.entity.AppUserRole;
import com.reconcilex.repository.AppUserRepository;

@Component
public class AdminUserInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserInitializer.class);

    @Value("${APP_ADMIN_USERNAME:${app.admin.username:}}")
    private String adminUsername;

    @Value("${APP_ADMIN_PASSWORD:${app.admin.password:}}")
    private String adminPassword;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserInitializer(AppUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (adminUsername == null || adminUsername.trim().isEmpty() ||
            adminPassword == null || adminPassword.trim().isEmpty()) {
            log.info("APP_ADMIN_USERNAME or APP_ADMIN_PASSWORD not set. Skipping initial admin user creation.");
            return;
        }

        String username = adminUsername.trim();
        String password = adminPassword.trim();

        if (password.length() < 12) {
            throw new IllegalStateException("APP_ADMIN_PASSWORD must be at least 12 characters long");
        }

        if (userRepository.existsByUsername(username)) {
            log.info("Initial admin user '{}' already exists.", username);
            return;
        }

        AppUser admin = new AppUser(username, passwordEncoder.encode(password), AppUserRole.ADMIN);
        userRepository.save(admin);
        log.info("Initial admin user '{}' created successfully.", username);
    }
}
