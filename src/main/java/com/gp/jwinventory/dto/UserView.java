package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.Role;

public record UserView(Long id, String email, String fullName, Role role, String congregationName, boolean enabled) {
}
