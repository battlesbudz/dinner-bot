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

    @Test fun sevenNightsDefined() {
        assertEquals(7, OnboardingDefaults.NIGHTS.size)
    }
}
