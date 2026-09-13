package com.gp.jwinventory.service;

import com.gp.jwinventory.entity.User;
import com.gp.jwinventory.enums.Role;
import com.gp.jwinventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));

        return org.springframework.security.core.userdetails.User.withUsername(user.getEmail())
                .password(user.getPassword())
                .authorities(authoritiesFor(user.getRole()))
                .disabled(!user.isEnabled())
                .build();
    }

    /** Hierarchical: every role also carries the authorities of the roles below it. */
    public static List<SimpleGrantedAuthority> authoritiesFor(Role role) {
        List<Role> roles = switch (role) {
            case SUPER_ADMIN -> List.of(Role.SUPER_ADMIN, Role.ADMIN, Role.USER);
            case ADMIN -> List.of(Role.ADMIN, Role.USER);
            case USER -> List.of(Role.USER);
        };
        return roles.stream().map(r -> new SimpleGrantedAuthority(r.name())).toList();
    }
}
