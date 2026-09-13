package com.gp.jwinventory.entity;

import com.gp.jwinventory.enums.NotificationScope;
import com.gp.jwinventory.enums.ScheduleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "scheduled_notifications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    /** Normalized, comma-separated list of email addresses. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipients;

    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_type", nullable = false, length = 20)
    private ScheduleType scheduleType;

    @Column(name = "run_date")
    private LocalDate runDate;

    /** ISO day of week: 1 = Monday ... 7 = Sunday. */
    @Column(name = "day_of_week")
    private Integer dayOfWeek;

    @Column(name = "day_of_month")
    private Integer dayOfMonth;

    @Column(name = "send_time", nullable = false)
    private LocalTime sendTime;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationScope scope;

    /** Creator's congregation at creation time; null only for SUPER_ADMIN scope (DB check constraint). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "congregation_id")
    private Congregation congregation;

    /** Null when the owner was deleted (ADMIN-scope notifications outlive their creator). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(name = "next_run_at")
    private LocalDateTime nextRunAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
