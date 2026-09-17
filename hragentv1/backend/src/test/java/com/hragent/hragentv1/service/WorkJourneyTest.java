package com.hragent.hragentv1.service;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class WorkJourneyTest {
    LocalDate today=LocalDate.of(2026,9,8);
    @Test void firstThirtyDaysOnly(){assertTrue(WorkJourney.onboarding(today,today));assertTrue(WorkJourney.onboarding(today.minusDays(29),today));assertFalse(WorkJourney.onboarding(today.minusDays(30),today));assertFalse(WorkJourney.onboarding(today.minusYears(2),today));}
    @Test void unknownDateUsesWeekly(){assertFalse(WorkJourney.onboarding(null,today));}
    @Test void beforeStart(){assertTrue(WorkJourney.onboarding(today.plusDays(3),today));}
    @Test void weekResetsOnMonday(){var monday=LocalDate.of(2026,9,7);assertEquals(WorkJourney.weekly(monday).get(0)[0],WorkJourney.weekly(monday.plusDays(6)).get(0)[0]);assertNotEquals(WorkJourney.weekly(monday).get(0)[0],WorkJourney.weekly(monday.plusDays(7)).get(0)[0]);assertEquals(5,WorkJourney.weekly(monday).size());}
}

