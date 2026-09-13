package com.gp.jwinventory.config;

import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * The one and only SuperAdmin is defined by configuration: created on first start, and
 * kept aligned (email, password, enabled) with app.super-admin.* on every start.
 * A DB partial unique index guarantees no second SuperAdmin can exist.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SuperAdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Value("${app.super-admin.email}")
    private String email;

    @Value("${app.super-admin.password}")
    private String password;

    @Value("${app.super-admin.full-name}")
    private String fullName;

    @Override
    public void run(ApplicationArguments args) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        userRepository.findByRole(Role.SUPER_ADMIN).ifPresentOrElse(
                superAdmin -> {
                    boolean changed = false;
                    if (!superAdmin.getEmail().equals(normalizedEmail)) {
                        superAdmin.setEmail(normalizedEmail);
                        changed = true;
                    }
                    if (!passwordEncoder.matches(password, superAdmin.getPassword())) {
                        superAdmin.setPassword(passwordEncoder.encode(password));
                        changed = true;
                    }
                    if (!superAdmin.isEnabled()) {
                        superAdmin.setEnabled(true);
                        changed = true;
                    }
                    if (changed) {
                        userRepository.save(superAdmin);
                        log.info("SuperAdmin credentials aligned with configuration: {}", normalizedEmail);
                    }
                },
                () -> {
                    userRepository.save(User.builder()
                            .email(normalizedEmail)
                            .password(passwordEncoder.encode(password))
                            .fullName(fullName)
                            .role(Role.SUPER_ADMIN)
                            .createdAt(LocalDateTime.now(clock))
                            .build());
                    log.info("SuperAdmin created: {}", normalizedEmail);
                }
        );
    }
}
