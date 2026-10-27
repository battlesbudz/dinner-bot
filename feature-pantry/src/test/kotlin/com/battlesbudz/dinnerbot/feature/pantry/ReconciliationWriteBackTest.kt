package com.battlesbudz.dinnerbot.feature.pantry

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-7: the reconciliation write-back corrects pantry owned state
 * and bulk stock before planning, so the planner never assumes a
 * confirmed-missing item is home.
 */
class ReconciliationWriteBackTest {

    private fun item(id: String, name: String, store: String) = PriceItem(
        id = id, name = name, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-22", source = "test",
        freezeNote = if (id == "pork-tenderloin") "Portioned into bags." else null,
    )

    private val tenderloin = item("pork-tenderloin", "Pork tenderloin", "sams-club")
    private val potatoes = item("russet-potatoes", "Russet potatoes", "aldi")
    private val spaghetti = item("spaghetti", "Spaghetti", "aldi")
    private val book = listOf(tenderloin, potatoes, spaghetti)

    private val pantry = listOf(
        PantryItem("olive-oil", "Olive oil", owned = true),
        PantryItem("potatoes", "Potatoes", owned = true),
        PantryItem("salt", "Salt", owned = true),
    )
    private val stocks = listOf(
        BulkStock("pork-tenderloin", Quantity.pounds(4.5)),
    )

    @Test fun stapleFlipBulkZeroAndCoveringFlip() {
        val outcome = ReconciliationWriteBack.apply(
            pantryItems = pantry,
            bulkStocks = stocks,
            resolvedOuts = listOf(
                // A staple marked while cooking.
                ReconciledOut(
                    itemKey = "pantry:olive oil", displayName = "Olive oil",
                    stapleSide = true, pantryStapleName = "Olive oil",
                ),
                // A priced item ran out: the pantry entry covering it flips.
                ReconciledOut(
                    itemKey = "item:russet-potatoes", displayName = "Russet potatoes",
                    stapleSide = true, priceItemId = "russet-potatoes",
                ),
                // A Sam's bulk item ran out: its stock row is gone.
                ReconciledOut(
                    itemKey = "item:pork-tenderloin", displayName = "Pork tenderloin",
                    stapleSide = true, priceItemId = "pork-tenderloin", bulkItemId = "pork-tenderloin",
                ),
            ),
            priceItems = book,
        )
        val ownedById = outcome.pantryItems.associate { it.id to it.owned }
        assertFalse(ownedById.getValue("olive-oil"))
        assertFalse(ownedById.getValue("potatoes"))
        assertTrue(ownedById.getValue("salt"))
        assertTrue(outcome.bulkStocks.isEmpty())
    }

    @Test fun uncoveredPricedItemFlipsNothing() {
        val outcome = ReconciliationWriteBack.apply(
            pantryItems = pantry,
            bulkStocks = stocks,
            resolvedOuts = listOf(
                ReconciledOut(
                    itemKey = "item:spaghetti", displayName = "Spaghetti",
                    stapleSide = false, priceItemId = "spaghetti",
                ),
            ),
            priceItems = book,
        )
        assertEquals(pantry, outcome.pantryItems)
        assertEquals(stocks, outcome.bulkStocks)
    }

    @Test fun emptyResolutionIsIdentity() {
        val outcome = ReconciliationWriteBack.apply(pantry, stocks, emptyList(), book)
        assertEquals(pantry, outcome.pantryItems)
        assertEquals(stocks, outcome.bulkStocks)
    }
}
