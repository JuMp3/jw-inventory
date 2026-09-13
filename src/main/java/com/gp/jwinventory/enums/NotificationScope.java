package com.gp.jwinventory.enums;

/**
 * Fixed at creation from the creator's role. ADMIN notifications are shared by all
 * admins (editable) and all users (read-only); USER notifications are private to their owner.
 */
public enum NotificationScope {
    ADMIN,
    USER
}
