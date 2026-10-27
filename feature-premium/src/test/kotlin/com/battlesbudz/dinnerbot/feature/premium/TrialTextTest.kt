package com.battlesbudz.dinnerbot.feature.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The trial wording comes from the store's own free-phase period, so
 * the reading of ISO-8601 periods is pinned down here: a wrong read
 * would put a trial length on the paywall that the store never
 * published.
 */
class TrialTextTest {

    @Test
    fun periodLabelReadsSimplePeriods() {
        assertEquals("14 days", TrialText.periodLabel("P14D"))
        assertEquals("7 days", TrialText.periodLabel("P7D"))
        assertEquals("1 day", TrialText.periodLabel("P1D"))
        assertEquals("2 weeks", TrialText.periodLabel("P2W"))
        assertEquals("1 week", TrialText.periodLabel("P1W"))
        assertEquals("1 month", TrialText.periodLabel("P1M"))
        assertEquals("3 months", TrialText.periodLabel("P3M"))
        assertEquals("1 year", TrialText.periodLabel("P1Y"))
    }

    @Test
    fun periodLabelRejectsAnythingUnsimple() {
        assertNull(TrialText.periodLabel(null))
        assertNull(TrialText.periodLabel(""))
        assertNull(TrialText.periodLabel("P1W2D"))
        assertNull(TrialText.periodLabel("PT24H"))
        assertNull(TrialText.periodLabel("P0D"))
        assertNull(TrialText.periodLabel("two weeks"))
    }

    @Test
    fun trialLabelWordsTheDecidedAnnualTrial() {
        assertEquals(
            "14-day free trial, then $39.99/year",
            TrialText.trialLabel("P14D", "$39.99"),
        )
        // Play also words a two-week phase as 14 days.
        assertEquals(
            "14-day free trial, then $39.99/year",
            TrialText.trialLabel("P2W", "$39.99"),
        )
        assertEquals(
            "7-day free trial, then $39.99/year",
            TrialText.trialLabel("P1W", "$39.99"),
        )
        assertEquals(
            "1-month free trial, then $39.99/year",
            TrialText.trialLabel("P1M", "$39.99"),
        )
    }

    @Test
    fun trialLabelIsAbsentWithoutAReadableFreePhase() {
        assertNull(TrialText.trialLabel(null, "$39.99"))
        assertNull(TrialText.trialLabel("P1W2D", "$39.99"))
    }
}
