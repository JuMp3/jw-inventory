package com.gp.jwinventory.dto;

import com.gp.jwinventory.enums.NotificationScope;

import java.time.LocalDateTime;

public record NotificationView(Long id, String name, String subject, String recipients, int recipientCount,
                               String scheduleDescription, boolean enabled, NotificationScope scope,
                               String ownerName, LocalDateTime nextRunAt, LocalDateTime lastSentAt,
                               String lastError, boolean editable) {
}
