package com.battlesbudz.dinnerbot.feature.`import`

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Phase RI-2, plan section 5.4: the strict matcher. Only normalized
 * whole-name equality matches silently; anything else is at most a
 * suggestion Andrea taps herself. The near-name traps are pinned here.
 */
class ImportMatcherTest {

    private fun item(id: String, name: String) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = Store.ALDI, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-24", source = "test",
    )

    private val book = listOf(
        item("yellow-onions", "Yellow onions"),
        item("green-onions", "Green onions (scallions)"),
        item("russet-potatoes", "Russet potatoes"),
        item("rice", "Rice"),
        item("olive-oil", "Olive oil"),
    )

    @Test fun exactWholeNameMatches() {
        assertEquals("rice", ImportMatcher.match("Rice", book)?.id)
        assertEquals("olive-oil", ImportMatcher.match("olive oil", book)?.id)
    }

    @Test fun pluralAndCaseFoldToTheSameName() {
        assertEquals("yellow-onions", ImportMatcher.match("Yellow onion", book)?.id)
        assertEquals("russet-potatoes", ImportMatcher.match("RUSSET POTATOES", book)?.id)
    }

    @Test fun springOnionNeverMatchesYellowOnion() {
        // The pinned trap (plan section 5.4): a spring onion is not a
        // yellow onion, and no reading of the matcher may say it is.
        assertNull(ImportMatcher.match("Spring onion", book))
        assertNull(ImportMatcher.match("Spring onions", book))
    }

    @Test fun springOnionIsNeverEvenSuggestedTowardYellowOnion() {
        assertNull(ImportMatcher.suggestion("Spring onion", book))
        assertNull(ImportMatcher.suggestion("Spring onions", book))
    }

    @Test fun partialNameSuggestsButNeverMatches() {
        assertNull(ImportMatcher.match("Onion", book))
        assertEquals("yellow-onions", ImportMatcher.suggestion("Onion", book)?.id)
        assertNull(ImportMatcher.match("Potatoes", book))
        assertEquals("russet-potatoes", ImportMatcher.suggestion("Potatoes", book)?.id)
    }

    @Test fun aNameSpelledInFullGetsNoSuggestion() {
        // Equality is the matcher's job, not the suggestion's.
        assertEquals("rice", ImportMatcher.match("Rice", book)?.id)
        assertNull(ImportMatcher.suggestion("Rice", book))
    }

    @Test fun ambiguousNamesMatchNothingAndSuggestNothing() {
        val doubled = book + item("rice-2", "Rice")
        assertNull(ImportMatcher.match("Rice", doubled))
        assertNull(ImportMatcher.suggestion("Rice", doubled))
    }

    @Test fun unrelatedNameGetsNothing() {
        assertNull(ImportMatcher.match("Dragon fruit", book))
        assertNull(ImportMatcher.suggestion("Dragon fruit", book))
    }
}
