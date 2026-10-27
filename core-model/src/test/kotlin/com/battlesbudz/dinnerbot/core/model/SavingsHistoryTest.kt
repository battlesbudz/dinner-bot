package com.battlesbudz.dinnerbot.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase C3: savings history — per-week savings, this-month versus
 * all-time totals, and the consecutive under-budget streak.
 */
class SavingsHistoryTest {

    private val today = LocalDate.of(2026, 10, 20)

    /** An under-budget week by default: $100.00 budget, $80.00 needed, $20.00 saved. */
    private fun week(weekStart: String, budgetCents: Int = 10_000, cashNeededCents: Int = 8_000) =
        SavingsWeek(weekStart = weekStart, budgetCents = budgetCents, cashNeededCents = cashNeededCents)

    // -- One week's arithmetic ------------------------------------------

    @Test fun aWeekSavesThePartOfItsBudgetItDidNotNeed() {
        assertEquals(2_000, week("2026-10-05", budgetCents = 10_000, cashNeededCents = 8_000).savedCents)
        assertTrue(week("2026-10-05", budgetCents = 10_000, cashNeededCents = 8_000).underBudget)
    }

    @Test fun anOverBudgetWeekSavesZeroAndIsNotUnderBudget() {
        val over = week("2026-10-05", budgetCents = 10_000, cashNeededCents = 12_500)
        // 0, not -2500: an over-budget week never drags the totals down.
        assertEquals(0, over.savedCents)
        assertFalse(over.underBudget)
    }

    @Test fun aWeekNeedingExactlyItsBudgetIsUnderBudgetAndSavesZero() {
        val exact = week("2026-10-05", budgetCents = 10_000, cashNeededCents = 10_000)
        assertEquals(0, exact.savedCents)
        assertTrue(exact.underBudget)
    }

    // -- Totals -----------------------------------------------------------

    @Test fun emptyHistoryReportsAllZeros() {
        val report = SavingsHistory.report(emptyList(), today)
        assertEquals(0, report.savedThisMonthCents)
        assertEquals(0, report.totalSavedCents)
        assertEquals(0, report.currentUnderBudgetStreakWeeks)
        assertEquals(0, report.weeksPlanned)
        assertEquals(0, report.weeksUnderBudget)
    }

    @Test fun thisMonthSumsOnlyWeeksStartingInTodaysMonth() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-05", cashNeededCents = 8_000), // saves 2000, this month
                week("2026-10-12", cashNeededCents = 9_000), // saves 1000, this month
                week("2026-09-28", cashNeededCents = 5_000), // saves 5000, last month
                week("2025-10-06", cashNeededCents = 5_000), // saves 5000, same month last year
            ),
            today,
        )
        assertEquals(3_000, report.savedThisMonthCents)
        assertEquals(13_000, report.totalSavedCents)
        assertEquals(4, report.weeksPlanned)
        assertEquals(4, report.weeksUnderBudget)
    }

    @Test fun overBudgetWeeksAddNothingToTheTotalsAndDoNotCountAsUnderBudget() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-05", cashNeededCents = 8_000),
                week("2026-10-12", cashNeededCents = 15_000),
            ),
            today,
        )
        assertEquals(2_000, report.savedThisMonthCents)
        assertEquals(2_000, report.totalSavedCents)
        assertEquals(2, report.weeksPlanned)
        assertEquals(1, report.weeksUnderBudget)
    }

    // -- Streak -------------------------------------------------------------

    @Test fun streakCountsConsecutiveUnderBudgetWeeksSevenDaysApart() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-12"),
                week("2026-10-05"),
            ),
            today,
        )
        assertEquals(3, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun streakDoesNotDependOnInputOrder() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-05"),
                week("2026-10-19"),
                week("2026-10-12"),
            ),
            today,
        )
        assertEquals(3, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun aSingleUnderBudgetWeekIsAStreakOfOne() {
        val report = SavingsHistory.report(listOf(week("2026-10-19")), today)
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun aGapWeekBreaksTheStreak() {
        // 2026-10-12 is missing: 10-19 back to 10-05 is 14 days, not 7.
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-05"),
            ),
            today,
        )
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun aGapFurtherBackEndsALongerStreakThere() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-12"),
                week("2026-09-28"), // 14 days before 10-12, not 7
                week("2026-09-21"),
            ),
            today,
        )
        assertEquals(2, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun weeksCloserThanSevenDaysApartBreakTheStreak() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-15"), // only 4 days earlier
            ),
            today,
        )
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun anOverBudgetLatestWeekMakesTheStreakZero() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19", cashNeededCents = 12_000),
                week("2026-10-12"),
                week("2026-10-05"),
            ),
            today,
        )
        assertEquals(0, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun anOverBudgetWeekInTheMiddleEndsTheStreakThere() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-12", cashNeededCents = 12_000),
                week("2026-10-05"),
            ),
            today,
        )
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun anOlderOverBudgetWeekDoesNotShortenARecentStreak() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-12"),
                week("2026-10-05", cashNeededCents = 12_000),
                week("2026-09-28"),
            ),
            today,
        )
        assertEquals(2, report.currentUnderBudgetStreakWeeks)
    }

    // -- Duplicate week starts ----------------------------------------------

    @Test fun duplicateWeekStartLastRecordWinsForTheStreak() {
        // The first record for 2026-10-19 says over budget; the later,
        // corrected record for the same week says under budget.
        val correctedToUnder = SavingsHistory.report(
            listOf(
                week("2026-10-19", cashNeededCents = 12_000),
                week("2026-10-12"),
                week("2026-10-19", cashNeededCents = 8_000),
            ),
            today,
        )
        assertEquals(2, correctedToUnder.currentUnderBudgetStreakWeeks)

        // And the other way round: a correction to over budget zeroes it.
        val correctedToOver = SavingsHistory.report(
            listOf(
                week("2026-10-19", cashNeededCents = 8_000),
                week("2026-10-12"),
                week("2026-10-19", cashNeededCents = 12_000),
            ),
            today,
        )
        assertEquals(0, correctedToOver.currentUnderBudgetStreakWeeks)
    }

    @Test fun duplicateWeekStartDoesNotCountAsTwoStreakWeeks() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-19"),
                week("2026-10-19"),
            ),
            today,
        )
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    // -- Unparseable week starts ----------------------------------------------

    @Test fun unparseableWeekStartsAreSkippedEverywhere() {
        val report = SavingsHistory.report(
            listOf(
                week("2026-10-12", cashNeededCents = 8_000), // the one valid week
                week("not-a-date", cashNeededCents = 1_000),
                week("", cashNeededCents = 1_000),
                week("2026-13-05", cashNeededCents = 1_000), // no 13th month
                week("2026-02-30", cashNeededCents = 1_000), // no such day
            ),
            today,
        )
        assertEquals(1, report.weeksPlanned)
        assertEquals(1, report.weeksUnderBudget)
        assertEquals(2_000, report.totalSavedCents)
        assertEquals(2_000, report.savedThisMonthCents)
        assertEquals(1, report.currentUnderBudgetStreakWeeks)
    }

    @Test fun aHistoryOfOnlyUnparseableWeeksReportsAllZeros() {
        val report = SavingsHistory.report(
            listOf(week("garbage"), week("10/05/2026")),
            today,
        )
        assertEquals(0, report.weeksPlanned)
        assertEquals(0, report.weeksUnderBudget)
        assertEquals(0, report.totalSavedCents)
        assertEquals(0, report.savedThisMonthCents)
        assertEquals(0, report.currentUnderBudgetStreakWeeks)
    }
}
