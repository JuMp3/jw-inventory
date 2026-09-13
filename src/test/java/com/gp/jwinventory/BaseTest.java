package com.gp.jwinventory;

import com.gp.jwinventory.config.PostgresTestContainerConfig;
import com.gp.jwinventory.entity.Congregation;
import com.gp.jwinventory.entity.Publication;
import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.repository.CongregationRepository;
import com.gp.jwinventory.repository.PublicationRepository;
import com.gp.jwinventory.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;

/**
 * Real PostgreSQL (TestContainers); only the SMTP sender is mocked. The SuperAdmin
 * (superadmin@test.com) is created by SuperAdminBootstrap: fetch it, never recreate it.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainerConfig.class)
public abstract class BaseTest {

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected CongregationRepository congregationRepository;

    @Autowired
    protected PublicationRepository publicationRepository;

    @MockitoBean
    protected JavaMailSender mailSender;

    protected Congregation newCongregation(String name) {
        return congregationRepository.save(Congregation.builder().name(name).createdAt(LocalDateTime.now()).build());
    }

    /** Password is irrelevant for service tests; the hash is a fixed placeholder. */
    protected User newUser(String email, Role role, Congregation congregation) {
        return userRepository.save(User.builder()
                .email(email)
                .password("{noop}unused")
                .fullName(email.substring(0, email.indexOf('@')))
                .role(role)
                .congregation(congregation)
                .createdAt(LocalDateTime.now())
                .build());
    }

    protected Publication newPublication(String code, int quantity) {
        LocalDateTime now = LocalDateTime.now();
        return publicationRepository.save(Publication.builder()
                .code(code)
                .title("Titolo " + code)
                .quantity(quantity)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }

    protected User superAdmin() {
        return userRepository.findByRole(Role.SUPER_ADMIN).orElseThrow();
    }
}
