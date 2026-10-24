package com.battlesbudz.dinnerbot.feature.pricebook

import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PriceCorrectionTest {

    private val onions = PriceItem(
        id = "yellow-onions", name = "Yellow onions", category = "Produce",
        homeStore = Store.ALDI, packDescription = "3 lb bag",
        packQuantity = Quantity(3.0, MeasureUnit.POUND), packPriceCents = 219,
        pricePerUnitCents = 73.0, checkedDate = "2026-10-21", source = "seed",
    )

    @Test fun aRealPriceWriteClearsTheGuessMark() {
        // Battles's correction (2026-10-24): the first scan or typed
        // correction on a guess-priced item writes the real price and
        // the guess is gone for good. A home-store move is not a price
        // and leaves the mark standing.
        val guessed = onions.copy(guessPriced = true)
        val corrected = PriceCorrection.applyPackPrice(guessed, 249)
        assertEquals(249, corrected.packPriceCents)
        assertTrue(!corrected.guessPriced)
        assertTrue(corrected.userCorrected)
        val moved = PriceCorrection.applyHomeStore(guessed, Store.WALMART)
        assertTrue(moved.guessPriced)
    }

    @Test fun parsesTypedPrices() {
        assertEquals(249, PriceCorrection.parseToCents("2.49"))
        assertEquals(300, PriceCorrection.parseToCents("\$3"))
        assertEquals(250, PriceCorrection.parseToCents(" 2.5 "))
        assertNull(PriceCorrection.parseToCents("abc"))
        assertNull(PriceCorrection.parseToCents(""))
        assertNull(PriceCorrection.parseToCents("-1.00"))
    }

    @Test fun correctionWinsAndRecomputesPerUnit() {
        val corrected = PriceCorrection.applyPackPrice(onions, 300)
        assertEquals(300, corrected.packPriceCents)
        assertEquals(100.0, corrected.pricePerUnitCents!!, 0.001)
        assertTrue(corrected.userCorrected)
    }

    @Test fun homeStoreMoveKeepsPrice() {
        val moved = PriceCorrection.applyHomeStore(onions, Store.WALMART)
        assertEquals(Store.WALMART, moved.homeStore)
        assertEquals(219, moved.packPriceCents)
    }

    @Test fun alternatePriceReplacesExistingForThatStore() {
        val item = onions.copy(
            homeStore = Store.SAMS_CLUB,
            alternates = listOf(StorePrice(Store.ALDI, 297.0), StorePrice(Store.WALMART, 417.0)),
        )
        val updated = PriceCorrection.applyAlternatePrice(item, Store.ALDI, 289.0)
        assertEquals(289.0, updated.alternates.single { it.store == Store.ALDI }.pricePerUnitCents, 0.001)
        assertEquals(417.0, updated.alternates.single { it.store == Store.WALMART }.pricePerUnitCents, 0.001)
        assertTrue(updated.userCorrected)
    }
}
