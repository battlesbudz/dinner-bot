package com.battlesbudz.dinnerbot.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C3: the freemium rules — sequential scan allowances, the monthly
 * import cap, the gate matrix, fail-soft entitlement, and the quiet
 * periodic-prompt cadence.
 */
class PremiumRulesTest {

    private val october = LocalDate.of(2026, 10, 15)
    private val november = LocalDate.of(2026, 11, 2)
    private val dayMillis = 24L * 60 * 60 * 1000

    // -- Month keys -----------------------------------------------------

    @Test fun monthKeyIsZeroPaddedYearAndMonth() {
        assertEquals("2026-10", PremiumRules.monthKey(october))
        assertEquals("2026-01", PremiumRules.monthKey(LocalDate.of(2026, 1, 5)))
        assertEquals("2026-12", PremiumRules.monthKey(LocalDate.of(2026, 12, 31)))
    }

    // -- Barcode scans: starter first, then monthly ---------------------

    @Test fun newUserHasTheFullStarterAllowanceOnly() {
        // 15, not 15 + 10: the monthly pool has not opened yet.
        assertEquals(15, PremiumRules.scansRemaining(UsageCounters(), october))
        assertTrue(PremiumRules.canScan(isPremium = false, UsageCounters(), october))
    }

    @Test fun starterScansAreConsumedFirstAcrossCalls() {
        var counters = UsageCounters()
        repeat(5) { counters = PremiumRules.afterScan(counters, october) }
        assertEquals(5, counters.starterScansUsed)
        assertEquals(0, counters.scansUsedInMonth)
        assertEquals(10, PremiumRules.scansRemaining(counters, october))
    }

    @Test fun monthlyPoolStaysUntouchedUntilTheStarterAllowanceIsGone() {
        var counters = UsageCounters()
        repeat(PremiumRules.STARTER_SCAN_ALLOWANCE) {
            counters = PremiumRules.afterScan(counters, october)
        }
        assertEquals(PremiumRules.STARTER_SCAN_ALLOWANCE, counters.starterScansUsed)
        assertEquals(0, counters.scansUsedInMonth)
        // The starter pool is empty, so what remains is the monthly pool, whole.
        assertEquals(PremiumRules.MONTHLY_SCAN_ALLOWANCE, PremiumRules.scansRemaining(counters, october))
    }

    @Test fun scansAfterTheStarterAllowanceComeFromTheMonthlyPool() {
        var counters = UsageCounters(starterScansUsed = PremiumRules.STARTER_SCAN_ALLOWANCE)
        counters = PremiumRules.afterScan(counters, october)
        assertEquals(PremiumRules.STARTER_SCAN_ALLOWANCE, counters.starterScansUsed)
        assertEquals(1, counters.scansUsedInMonth)
        assertEquals(9, PremiumRules.scansRemaining(counters, october))
    }

    @Test fun monthlyScansRunOutAfterTenOnceTheStarterAllowanceIsGone() {
        var counters = UsageCounters(starterScansUsed = PremiumRules.STARTER_SCAN_ALLOWANCE)
        repeat(PremiumRules.MONTHLY_SCAN_ALLOWANCE) {
            assertTrue(PremiumRules.canScan(isPremium = false, counters, october))
            counters = PremiumRules.afterScan(counters, october)
        }
        assertEquals(0, PremiumRules.scansRemaining(counters, october))
        assertFalse(PremiumRules.canScan(isPremium = false, counters, october))
    }

    @Test fun scansRemainingIsNeverNegative() {
        val overSpent = UsageCounters(
            starterScansUsed = 99,
            scanMonth = "2026-10",
            scansUsedInMonth = 99,
        )
        assertEquals(0, PremiumRules.scansRemaining(overSpent, october))
        assertFalse(PremiumRules.canScan(isPremium = false, overSpent, october))
    }

    @Test fun scanInANewMonthStillComesFromTheStarterPoolWhileItHasAnyLeft() {
        val counters = UsageCounters(
            starterScansUsed = 14,
            scanMonth = "2026-10",
            scansUsedInMonth = 3,
        )
        val after = PremiumRules.afterScan(counters, november)
        // The last starter scan is spent; the monthly count rolled to the
        // new month and was not touched by this scan.
        assertEquals(15, after.starterScansUsed)
        assertEquals("2026-11", after.scanMonth)
        assertEquals(0, after.scansUsedInMonth)
    }

    // -- Month rollover ---------------------------------------------------

    @Test fun rolloverResetsBothMonthlyPoolsButNeverTheStarterPool() {
        val counters = UsageCounters(
            starterScansUsed = 7,
            scanMonth = "2026-09",
            scansUsedInMonth = 8,
            importMonth = "2026-09",
            importsUsedInMonth = 2,
        )
        val rolled = PremiumRules.rolledCounters(counters, october)
        assertEquals(7, rolled.starterScansUsed)
        assertEquals("2026-10", rolled.scanMonth)
        assertEquals(0, rolled.scansUsedInMonth)
        assertEquals("2026-10", rolled.importMonth)
        assertEquals(0, rolled.importsUsedInMonth)
    }

    @Test fun rolloverLeavesCurrentMonthPoolsUntouched() {
        val counters = UsageCounters(
            starterScansUsed = 15,
            scanMonth = "2026-10",
            scansUsedInMonth = 4,
            importMonth = "2026-10",
            importsUsedInMonth = 1,
        )
        assertEquals(counters, PremiumRules.rolledCounters(counters, october))
    }

    @Test fun scanAndImportMonthsRollIndependently() {
        val counters = UsageCounters(
            scanMonth = "2026-10",
            scansUsedInMonth = 4,
            importMonth = "2026-09",
            importsUsedInMonth = 2,
        )
        val rolled = PremiumRules.rolledCounters(counters, october)
        assertEquals(4, rolled.scansUsedInMonth)
        assertEquals("2026-10", rolled.scanMonth)
        assertEquals(0, rolled.importsUsedInMonth)
        assertEquals("2026-10", rolled.importMonth)
    }

    @Test fun nullMonthFieldsBecomeTheCurrentMonthWithZeroUsed() {
        val rolled = PremiumRules.rolledCounters(UsageCounters(starterScansUsed = 3), october)
        assertEquals("2026-10", rolled.scanMonth)
        assertEquals(0, rolled.scansUsedInMonth)
        assertEquals("2026-10", rolled.importMonth)
        assertEquals(0, rolled.importsUsedInMonth)
        assertEquals(3, rolled.starterScansUsed)
    }

    @Test fun nullMonthFieldsReadAsFullAllowances() {
        assertEquals(15, PremiumRules.scansRemaining(UsageCounters(), october))
        assertEquals(2, PremiumRules.importsRemaining(UsageCounters(), october))
        // Even a stored monthly count reads as unspent when its month was
        // never stamped: the count belongs to no known month.
        val unstamped = UsageCounters(
            starterScansUsed = PremiumRules.STARTER_SCAN_ALLOWANCE,
            scanMonth = null,
            scansUsedInMonth = 6,
            importMonth = null,
            importsUsedInMonth = 2,
        )
        assertEquals(10, PremiumRules.scansRemaining(unstamped, october))
        assertEquals(2, PremiumRules.importsRemaining(unstamped, october))
    }

    @Test fun monthlyScansRefillInANewMonthButTheStarterPoolDoesNot() {
        val spent = UsageCounters(
            starterScansUsed = PremiumRules.STARTER_SCAN_ALLOWANCE,
            scanMonth = "2026-10",
            scansUsedInMonth = PremiumRules.MONTHLY_SCAN_ALLOWANCE,
        )
        assertEquals(0, PremiumRules.scansRemaining(spent, october))
        assertEquals(10, PremiumRules.scansRemaining(spent, november))

        val starterPartlySpent = UsageCounters(starterScansUsed = 6, scanMonth = "2026-10")
        assertEquals(9, PremiumRules.scansRemaining(starterPartlySpent, november))
    }

    // -- Premium bypass ---------------------------------------------------

    @Test fun premiumScansWithoutACap() {
        val exhausted = UsageCounters(
            starterScansUsed = PremiumRules.STARTER_SCAN_ALLOWANCE,
            scanMonth = "2026-10",
            scansUsedInMonth = PremiumRules.MONTHLY_SCAN_ALLOWANCE,
        )
        assertTrue(PremiumRules.canScan(isPremium = true, exhausted, october))
    }

    @Test fun premiumImportsWithoutACap() {
        val exhausted = UsageCounters(importMonth = "2026-10", importsUsedInMonth = 2)
        assertTrue(PremiumRules.canImport(isPremium = true, exhausted, october))
    }

    // -- Recipe imports ---------------------------------------------------

    @Test fun freeImportsAreCappedAtTwoPerCalendarMonth() {
        var counters = UsageCounters()
        assertEquals(2, PremiumRules.importsRemaining(counters, october))
        assertTrue(PremiumRules.canImport(isPremium = false, counters, october))

        counters = PremiumRules.afterImport(counters, october)
        assertEquals(1, counters.importsUsedInMonth)
        assertEquals("2026-10", counters.importMonth)
        assertEquals(1, PremiumRules.importsRemaining(counters, october))
        assertTrue(PremiumRules.canImport(isPremium = false, counters, october))

        counters = PremiumRules.afterImport(counters, october)
        assertEquals(2, counters.importsUsedInMonth)
        assertEquals(0, PremiumRules.importsRemaining(counters, october))
        assertFalse(PremiumRules.canImport(isPremium = false, counters, october))
    }

    @Test fun importCapResetsInANewMonth() {
        val spent = UsageCounters(importMonth = "2026-10", importsUsedInMonth = 2)
        assertEquals(0, PremiumRules.importsRemaining(spent, october))
        assertFalse(PremiumRules.canImport(isPremium = false, spent, october))
        assertEquals(2, PremiumRules.importsRemaining(spent, november))
        assertTrue(PremiumRules.canImport(isPremium = false, spent, november))
    }

    @Test fun importInANewMonthRollsBeforeCounting() {
        val spent = UsageCounters(importMonth = "2026-10", importsUsedInMonth = 2)
        val after = PremiumRules.afterImport(spent, november)
        assertEquals("2026-11", after.importMonth)
        // 1, not 3: the old month's two imports rolled away first.
        assertEquals(1, after.importsUsedInMonth)
    }

    @Test fun importsRemainingIsNeverNegative() {
        val overSpent = UsageCounters(importMonth = "2026-10", importsUsedInMonth = 99)
        assertEquals(0, PremiumRules.importsRemaining(overSpent, october))
    }

    // -- Gate matrix --------------------------------------------------------

    @Test fun thePremiumFeatureListIsExactlyTheFiveLockedFeatures() {
        assertEquals(5, PremiumFeature.entries.size)
        assertEquals(
            setOf(
                PremiumFeature.RECIPE_IMPORT,
                PremiumFeature.BARCODE_SCAN,
                PremiumFeature.TWO_WEEK_PLANNING,
                PremiumFeature.DIET_PRESETS,
                PremiumFeature.SAVINGS_HISTORY,
            ),
            PremiumFeature.entries.toSet(),
        )
    }

    @Test fun everyPremiumFeatureIsClosedToFreeAndOpenToPremium() {
        for (feature in PremiumFeature.entries) {
            assertFalse("free must not use $feature", PremiumRules.isAllowed(feature, isPremium = false))
            assertTrue("premium must be able to use $feature", PremiumRules.isAllowed(feature, isPremium = true))
        }
    }

    // -- Fail-soft entitlement ----------------------------------------------

    @Test fun aMissingFetchLeavesTheCachedStatusStanding() {
        assertTrue(PremiumRules.resolvePremium(cachedPremium = true, fetchedPremium = null))
        assertFalse(PremiumRules.resolvePremium(cachedPremium = false, fetchedPremium = null))
    }

    @Test fun aFetchedStatusWinsOverTheCacheBothWays() {
        assertTrue(PremiumRules.resolvePremium(cachedPremium = false, fetchedPremium = true))
        assertFalse(PremiumRules.resolvePremium(cachedPremium = true, fetchedPremium = false))
        assertTrue(PremiumRules.resolvePremium(cachedPremium = true, fetchedPremium = true))
        assertFalse(PremiumRules.resolvePremium(cachedPremium = false, fetchedPremium = false))
    }

    // -- Periodic prompt cadence ----------------------------------------------

    private val nowMillis = 1_700_000_000_000L

    /** A state that satisfies every prompt condition at [nowMillis]. */
    private fun eligibleState() = PremiumLocalState(
        cachedPremium = false,
        firstSeenAtMillis = nowMillis - 10 * dayMillis,
        lastPromptDismissedAtMillis = 0L,
    )

    private fun prompt(state: PremiumLocalState, onboardingComplete: Boolean = true, hasAnyPlan: Boolean = true) =
        PremiumRules.shouldShowPeriodicPrompt(state, onboardingComplete, hasAnyPlan, nowMillis)

    @Test fun promptShowsWhenEveryConditionIsMet() {
        assertTrue(prompt(eligibleState()))
    }

    @Test fun promptNeverShowsToPremiumUsers() {
        assertFalse(prompt(eligibleState().copy(cachedPremium = true)))
    }

    @Test fun promptNeverShowsDuringOnboarding() {
        assertFalse(prompt(eligibleState(), onboardingComplete = false))
    }

    @Test fun promptNeverShowsBeforeTheFirstPlan() {
        assertFalse(prompt(eligibleState(), hasAnyPlan = false))
    }

    @Test fun promptNeverShowsWhenTheStateWasNeverStamped() {
        assertFalse(prompt(eligibleState().copy(firstSeenAtMillis = 0L)))
    }

    @Test fun promptWaitsOutTheThreeDaySettleIn() {
        assertFalse(prompt(eligibleState().copy(firstSeenAtMillis = nowMillis - dayMillis)))
        assertFalse(
            prompt(eligibleState().copy(firstSeenAtMillis = nowMillis - PremiumRules.PROMPT_SETTLE_IN_MILLIS + 1)),
        )
    }

    @Test fun promptIsEligibleExactlyAtTheSettleInBoundary() {
        assertTrue(
            prompt(eligibleState().copy(firstSeenAtMillis = nowMillis - PremiumRules.PROMPT_SETTLE_IN_MILLIS)),
        )
    }

    @Test fun aDismissalSuppressesThePromptForThirtyDays() {
        assertFalse(prompt(eligibleState().copy(lastPromptDismissedAtMillis = nowMillis - 10 * dayMillis)))
        assertFalse(
            prompt(
                eligibleState().copy(
                    lastPromptDismissedAtMillis = nowMillis - PremiumRules.PROMPT_DISMISSAL_MILLIS + 1,
                ),
            ),
        )
    }

    @Test fun thePromptReturnsThirtyDaysAfterADismissal() {
        assertTrue(
            prompt(
                eligibleState().copy(
                    lastPromptDismissedAtMillis = nowMillis - PremiumRules.PROMPT_DISMISSAL_MILLIS,
                ),
            ),
        )
        assertTrue(prompt(eligibleState().copy(lastPromptDismissedAtMillis = nowMillis - 45 * dayMillis)))
    }

    @Test fun promptConstantsMatchTheLockedCadence() {
        assertEquals(3 * dayMillis, PremiumRules.PROMPT_SETTLE_IN_MILLIS)
        assertEquals(30 * dayMillis, PremiumRules.PROMPT_DISMISSAL_MILLIS)
        assertEquals(15, PremiumRules.STARTER_SCAN_ALLOWANCE)
        assertEquals(10, PremiumRules.MONTHLY_SCAN_ALLOWANCE)
        assertEquals(2, PremiumRules.MONTHLY_IMPORT_ALLOWANCE)
    }
}
