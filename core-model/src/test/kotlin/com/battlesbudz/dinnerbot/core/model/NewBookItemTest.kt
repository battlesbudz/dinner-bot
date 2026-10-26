package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * The shared add-an-ingredient builder (v0.6.2 hotfix): whichever
 * door Andrea adds an ingredient through — the recipe editor, the
 * price book, a barcode scan — the record that lands in the book is
 * built here, priced when she typed a price and guess-priced when
 * she did not, so a missing price can never block her again.
 */
class NewBookItemTest {

    private fun item(id: String, name: String, packPrice: Int, store: String = "aldi") =
        PriceItem(
            id = id, name = name, category = "Produce", homeStore = store,
            packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
            packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
            checkedDate = "2026-10-01", source = "test",
        )

    private val yellowOnion = item("yellow-onion", "Yellow onion", 129)
    private val redOnion = item("red-onion", "Red onion", 149)
    private val eggs = item("eggs", "Eggs", 185)
    private val book = listOf(yellowOnion, redOnion, eggs)

    @Test fun aTypedPriceBuildsAPricedUserCorrectedItem() {
        val built = NewBookItem.build(
            name = "Gochujang",
            category = "Sauces",
            homeStore = "walmart",
            packDescription = "Tub, 1 lb",
            packQuantity = Quantity(1.0, MeasureUnit.POUND),
            priceCents = 549,
            book = book,
            checkedDate = "2026-10-25",
        )
        assertEquals("user-gochujang", built.id)
        assertEquals("Gochujang", built.name)
        assertEquals("Sauces", built.category)
        assertEquals("walmart", built.homeStore)
        assertEquals("Tub, 1 lb", built.packDescription)
        assertEquals(549, built.packPriceCents)
        assertEquals(549.0, built.pricePerUnitCents!!, 0.001)
        assertEquals("2026-10-25", built.checkedDate)
        assertEquals(NewBookItem.HAND_PRICED_SOURCE, built.source)
        assertTrue("a price Andrea typed is her number and wins", built.userCorrected)
        assertFalse(built.guessPriced)
    }

    @Test fun noPriceBuildsAGuessPricedItemAtTheBookMedian() {
        // No book item shares the head noun "gochujang", so the whole
        // book's median (149) stands in — and the item still exists,
        // which is the whole point of the hotfix.
        val built = NewBookItem.build(
            name = "Gochujang",
            category = "",
            homeStore = "aldi",
            packDescription = "",
            packQuantity = Quantity(2.0, MeasureUnit.COUNT),
            priceCents = null,
            book = book,
            checkedDate = "2026-10-25",
        )
        assertEquals("user-gochujang", built.id)
        assertTrue(built.guessPriced)
        assertFalse(built.userCorrected)
        assertEquals(149, built.packPriceCents)
        assertEquals(NewBookItem.HAND_GUESS_SOURCE, built.source)
        assertEquals("Other", built.category)
        assertEquals("2 ct", built.packDescription)
    }

    @Test fun aGuessUsesEquivalentItemsSharingTheHeadNoun() {
        val built = NewBookItem.build(
            name = "White onion",
            category = "",
            homeStore = "aldi",
            packDescription = "",
            packQuantity = Quantity(1.0, MeasureUnit.POUND),
            priceCents = null,
            book = book,
            checkedDate = "2026-10-25",
        )
        // The two onions are the equivalents: median of 129 and 149.
        assertEquals(139, built.packPriceCents)
        assertEquals("Produce", built.category)
        assertTrue(built.guessPriced)
    }

    @Test fun anEmptyBookStillProducesAnItemAtTheFallbackGuess() {
        val built = NewBookItem.build(
            name = "Tahini",
            category = "Other",
            homeStore = "aldi",
            packDescription = "",
            packQuantity = Quantity(1.0, MeasureUnit.COUNT),
            priceCents = null,
            book = emptyList(),
            checkedDate = "2026-10-25",
        )
        assertEquals(GuessPricer.FALLBACK_GUESS_CENTS, built.packPriceCents)
        assertTrue(built.guessPriced)
    }

    @Test fun aSecondItemWithTheSameNameGetsANumberedId() {
        val first = NewBookItem.build(
            name = "Tahini", category = "Other", homeStore = "aldi",
            packDescription = "", packQuantity = Quantity(1.0, MeasureUnit.COUNT),
            priceCents = 429, book = book, checkedDate = "2026-10-25",
        )
        val second = NewBookItem.build(
            name = "Tahini", category = "Other", homeStore = "aldi",
            packDescription = "", packQuantity = Quantity(1.0, MeasureUnit.COUNT),
            priceCents = 459, book = book + first, checkedDate = "2026-10-25",
        )
        assertEquals("user-tahini", first.id)
        assertEquals("user-tahini-2", second.id)
    }

    @Test fun scanSourcesRideAlongWhenTheCallerNamesThem() {
        val built = NewBookItem.build(
            name = "Granulated sugar",
            category = "Dry and canned",
            homeStore = "aldi",
            packDescription = "Bag, 4 lb",
            packQuantity = Quantity(4.0, MeasureUnit.POUND),
            priceCents = 342,
            book = book,
            checkedDate = "2026-10-23",
            pricedSource = NewBookItem.SCAN_PRICED_SOURCE,
        )
        assertEquals("Added by barcode scan", built.source)
        assertEquals(85.5, built.pricePerUnitCents!!, 0.001)
        assertTrue(built.userCorrected)
    }

    @Test fun blankNamesAndBadNumbersAreRejected() {
        fun build(name: String, amount: Double, price: Int?) = NewBookItem.build(
            name = name, category = "Other", homeStore = "aldi",
            packDescription = "", packQuantity = Quantity(amount, MeasureUnit.COUNT),
            priceCents = price, book = book, checkedDate = "2026-10-25",
        )
        assertThrowsIllegalArgument { build("  ", 1.0, 100) }
        assertThrowsIllegalArgument { build("Tahini", 0.0, 100) }
        assertThrowsIllegalArgument { build("Tahini", 1.0, 0) }
        assertThrowsIllegalArgument { build("Tahini", 1.0, -5) }
    }

    private fun assertThrowsIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
