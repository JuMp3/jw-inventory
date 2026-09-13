package com.gp.jwinventory.service;

import com.gp.jwinventory.enums.ScheduleType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleRuleTest {

    private static final LocalTime NINE = LocalTime.of(9, 0);

    private static ScheduleRule monthly(int day) {
        return new ScheduleRule(ScheduleType.MONTHLY, null, null, day, NINE);
    }

    @Test
    void monthly_beforeTheDay_firesThisMonth() {
        assertThat(monthly(10).nextAfter(LocalDateTime.of(2026, 9, 5, 12, 0)))
                .contains(LocalDateTime.of(2026, 9, 10, 9, 0));
    }

    @Test
    void monthly_exactlyAtSendTime_movesToNextMonth() {
        assertThat(monthly(10).nextAfter(LocalDateTime.of(2026, 9, 10, 9, 0)))
                .contains(LocalDateTime.of(2026, 10, 10, 9, 0));
    }

    @Test
    void monthly_day31_isClampedToLastDayOfShortMonths() {
        assertThat(monthly(31).nextAfter(LocalDateTime.of(2026, 2, 1, 0, 0)))
                .contains(LocalDateTime.of(2026, 2, 28, 9, 0));
        assertThat(monthly(31).nextAfter(LocalDateTime.of(2026, 2, 28, 10, 0)))
                .contains(LocalDateTime.of(2026, 3, 31, 9, 0));
    }

    @Test
    void monthly_decemberRollsOverToJanuary() {
        assertThat(monthly(10).nextAfter(LocalDateTime.of(2026, 12, 20, 9, 0)))
                .contains(LocalDateTime.of(2027, 1, 10, 9, 0));
    }

    @Test
    void weekly_firesOnNextMatchingDay() {
        ScheduleRule monday = new ScheduleRule(ScheduleType.WEEKLY, null, 1, null, NINE);
        // 2026-09-13 is a Sunday
        assertThat(monday.nextAfter(LocalDateTime.of(2026, 9, 13, 10, 0)))
                .contains(LocalDateTime.of(2026, 9, 14, 9, 0));
        assertThat(monday.nextAfter(LocalDateTime.of(2026, 9, 14, 9, 30)))
                .contains(LocalDateTime.of(2026, 9, 21, 9, 0));
    }

    @Test
    void daily_firesTodayOrTomorrow() {
        ScheduleRule daily = new ScheduleRule(ScheduleType.DAILY, null, null, null, NINE);
        assertThat(daily.nextAfter(LocalDateTime.of(2026, 9, 13, 8, 0)))
                .contains(LocalDateTime.of(2026, 9, 13, 9, 0));
        assertThat(daily.nextAfter(LocalDateTime.of(2026, 9, 13, 9, 0)))
                .contains(LocalDateTime.of(2026, 9, 14, 9, 0));
    }

    @Test
    void once_futureFiresOnce_pastNever() {
        ScheduleRule once = new ScheduleRule(ScheduleType.ONCE, LocalDate.of(2026, 10, 1), null, null, NINE);
        assertThat(once.nextAfter(LocalDateTime.of(2026, 9, 13, 10, 0)))
                .contains(LocalDateTime.of(2026, 10, 1, 9, 0));
        assertThat(once.nextAfter(LocalDateTime.of(2026, 10, 1, 9, 0))).isEmpty();
    }

    @Test
    void describe_isHumanReadableItalian() {
        assertThat(monthly(10).describe()).isEqualTo("Il giorno 10 di ogni mese alle 09:00");
        assertThat(monthly(31).describe()).endsWith("(o l'ultimo giorno del mese)");
        assertThat(new ScheduleRule(ScheduleType.WEEKLY, null, 1, null, NINE).describe())
                .isEqualTo("Ogni lunedì alle 09:00");
    }
}
