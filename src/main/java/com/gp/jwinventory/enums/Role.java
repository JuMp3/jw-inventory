package com.gp.jwinventory.enums;

public enum Role {
    SUPER_ADMIN("Super Admin"),
    ADMIN("Admin"),
    USER("Utente");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Roles that can be assigned from the UI: a second SuperAdmin can never be created. */
    public boolean isAssignable() {
        return this != SUPER_ADMIN;
    }

    public boolean isAdmin() {
        return this == SUPER_ADMIN || this == ADMIN;
    }
}
