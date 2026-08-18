package com.hragent.hragentv1.service;

import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Service
public class WorkdayService {
    public List<LocalDate> workingDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return List.of();
        }
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(this::isWorkingDay)
                .toList();
    }

    public boolean isWorkingDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SATURDAY
                && date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    public boolean overlapsOnWorkingDay(
            LocalDate firstStart,
            LocalDate firstEnd,
            LocalDate secondStart,
            LocalDate secondEnd
    ) {
        LocalDate overlapStart = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        LocalDate overlapEnd = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return !overlapEnd.isBefore(overlapStart) && !workingDates(overlapStart, overlapEnd).isEmpty();
    }
}
