package com.battlesbudz.dinnerbot.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingDefaultsTest {
    @Test fun initialSettingsAreSane() {
        val s = OnboardingDefaults.initialSettings()
        assertEquals(4, s.householdSize)
        assertEquals(5, s.cookNights.size)
        assertFalse(s.onboardingComplete)
    }

    @Test fun staplesIncludeRiceAndSoySauce() {
        assertTrue("Long grain white rice" in OnboardingDefaults.DEFAULT_STAPLES)
        assertTrue("Soy sauce" in OnboardingDefaults.DEFAULT_STAPLES)
    }

    @Test fun suggestedBudgetScalesWithHouseholdAndNights() {
        // The documented heuristic: $3.50 per person per cook night,
        // rounded to the nearest $5.
        assertEquals(7000, OnboardingDefaults.suggestedBudgetCents(4, 5))
        assertEquals(2450, OnboardingDefaults.suggestedBudgetCents(2, 7))
        assertEquals(1050, OnboardingDefaults.suggestedBudgetCents(1, 3))
        assertEquals(0, OnboardingDefaults.suggestedBudgetCents(0, 5))
    }

    @Test fun initialSettingsPrefillTheSuggestedBudget() {
        // Phase C1 item 5: onboarding opens with the suggestion filled in
        // (editable), never a blank budget.
        val s = OnboardingDefaults.initialSettings()
        assertEquals(
            OnboardingDefaults.suggestedBudgetCents(s.householdSize, s.cookNights.size),
            s.weeklyBudgetCents,
        )
        assertTrue(s.weeklyBudgetCents > 0)
    }

    @Test fun initialSettingsPreTickNoStaples() {
        // Phase C1 item 5: the staples list is offered generic and
        // nothing is pre-ticked.
        assertTrue(OnboardingDefaults.initialSettings().pantryStaples.isEmpty())
    }

    @Test fun sevenNightsDefined() {
        assertEquals(7, OnboardingDefaults.NIGHTS.size)
    }
}
