package com.gp.jwinventory.enums;

/**
 * Fixed at creation from the creator's role. SUPER_ADMIN notifications are visible only to the
 * SuperAdmin; ADMIN notifications are shared by the admins (editable) and users (read-only) of the
 * creator's congregation; USER notifications are private to their owner. The SuperAdmin sees and
 * edits everything.
 */
public enum NotificationScope {
    SUPER_ADMIN,
    ADMIN,
    USER
}
