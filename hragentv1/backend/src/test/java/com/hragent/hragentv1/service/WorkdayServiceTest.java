package com.hragent.hragentv1.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WorkdayServiceTest {
    private final WorkdayService service = new WorkdayService();

    @Test
    void excludesAWeekendOnlyRange() {
        assertThat(service.workingDates(
                LocalDate.of(2026, 7, 25),
                LocalDate.of(2026, 7, 26)
        )).isEmpty();
    }

    @Test
    void countsOnlyFridayAndMondayAcrossAWeekend() {
        assertThat(service.workingDates(
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 21)
        )).containsExactly(
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 21)
        );
    }

    @Test
    void doesNotTreatWeekendOnlyOverlapAsAConflict() {
        assertThat(service.overlapsOnWorkingDay(
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 19),
                LocalDate.of(2026, 9, 20)
        )).isFalse();
    }

    @Test
    void treatsAWorkingDayOverlapAsAConflict() {
        assertThat(service.overlapsOnWorkingDay(
                LocalDate.of(2026, 9, 18),
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 22)
        )).isTrue();
    }
}
