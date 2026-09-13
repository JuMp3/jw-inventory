package com.gp.jwinventory.config;

import com.gp.jwinventory.enums.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Authorities are hierarchical (see CustomUserDetailsService): SUPER_ADMIN also has ADMIN
 * and USER, ADMIN also has USER. Data-dependent rules (congregation scoping, notification
 * ownership) are enforced in the services.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain webFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/js/**", "/favicon.svg", "/error",
                                "/actuator/health/**").permitAll()
                        .requestMatchers("/superadmin/**").hasAuthority(Role.SUPER_ADMIN.name())
                        .requestMatchers("/admin/**", "/publications/new", "/publications/*/edit",
                                "/publications/*/delete", "/inventory/*/cancel").hasAuthority(Role.ADMIN.name())
                        .requestMatchers("/", "/publications/**", "/inventory/**", "/movements/**",
                                "/notifications/**", "/profile/**").hasAuthority(Role.USER.name())
                        // Fail closed: a new route must be registered here explicitly
                        .anyRequest().denyAll()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
