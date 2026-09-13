package com.gp.jwinventory.service;

import com.gp.jwinventory.enums.ScheduleType;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Optional;

/**
 * When a notification fires. Only the field matching the type is meaningful:
 * runDate (ONCE), dayOfWeek 1-7 ISO (WEEKLY), dayOfMonth 1-31 (MONTHLY; clamped to the
 * last day for shorter months).
 */
public record ScheduleRule(ScheduleType type, LocalDate runDate, Integer dayOfWeek, Integer dayOfMonth,
                           LocalTime sendTime) {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** First occurrence strictly after the given instant, or empty when there is none (past ONCE). */
    public Optional<LocalDateTime> nextAfter(LocalDateTime after) {
        return switch (type) {
            case ONCE -> {
                LocalDateTime at = runDate.atTime(sendTime);
                yield at.isAfter(after) ? Optional.of(at) : Optional.empty();
            }
            case DAILY -> {
                LocalDateTime at = after.toLocalDate().atTime(sendTime);
                yield Optional.of(at.isAfter(after) ? at : at.plusDays(1));
            }
            case WEEKLY -> {
                LocalDateTime at = after.toLocalDate()
                        .with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dayOfWeek)))
                        .atTime(sendTime);
                yield Optional.of(at.isAfter(after) ? at : at.plusWeeks(1));
            }
            case MONTHLY -> {
                YearMonth month = YearMonth.from(after);
                LocalDateTime at = monthlyOccurrence(month);
                yield Optional.of(at.isAfter(after) ? at : monthlyOccurrence(month.plusMonths(1)));
            }
        };
    }

    public String describe() {
        String time = sendTime.format(TIME);
        return switch (type) {
            case ONCE -> "Una volta, il " + runDate.format(DATE) + " alle " + time;
            case DAILY -> "Ogni giorno alle " + time;
            case WEEKLY -> "Ogni " + DayOfWeek.of(dayOfWeek).getDisplayName(TextStyle.FULL, Locale.ITALIAN)
                    + " alle " + time;
            case MONTHLY -> "Il giorno " + dayOfMonth + " di ogni mese alle " + time
                    + (dayOfMonth > 28 ? " (o l'ultimo giorno del mese)" : "");
        };
    }

    private LocalDateTime monthlyOccurrence(YearMonth month) {
        return month.atDay(Math.min(dayOfMonth, month.lengthOfMonth())).atTime(sendTime);
    }
}
