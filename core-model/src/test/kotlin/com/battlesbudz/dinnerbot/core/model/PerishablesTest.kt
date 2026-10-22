package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-week freshness split (Phase V2-3) is category-based: Produce,
 * Dairy and eggs, and Meat are perishable; Dry and canned, Frozen, and
 * Bread keep for the fortnight. A Sam's Club line is a bulk stock purchase
 * and never splits, whatever its category.
 */
class PerishablesTest {

    private fun item(id: String, category: String, store: Store) = PriceItem(
        id = id, name = id, category = category, homeStore = store, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        checkedDate = "2026-10-22", source = "test",
    )

    private fun line(itemId: String, store: Store) = GroceryLine(
        itemId = itemId, displayName = itemId, store = store,
        needed = Quantity(1.0, MeasureUnit.POUND), packsToBuy = 1, estimatedPackPriceCents = 100,
    )

    @Test fun perishableCategoriesAreFreshFood() {
        assertTrue(Perishables.isPerishable(item("russet-potatoes", "Produce", Store.ALDI)))
        assertTrue(Perishables.isPerishable(item("milk", "Dairy and eggs", Store.ALDI)))
        assertTrue(Perishables.isPerishable(item("chicken-breast", "Meat", Store.ALDI)))
    }

    @Test fun shelfStableAndFrozenCategoriesAreNotPerishable() {
        assertFalse(Perishables.isPerishable(item("spaghetti", "Dry and canned", Store.ALDI)))
        assertFalse(Perishables.isPerishable(item("frozen-broccoli", "Frozen", Store.WALMART)))
        assertFalse(Perishables.isPerishable(item("white-bread", "Bread", Store.ALDI)))
    }

    @Test fun week2PerishableLineAtARegularStoreIsTopUp() {
        val milk = item("milk", "Dairy and eggs", Store.ALDI)
        assertTrue(Perishables.isTopUpLine(line("milk", Store.ALDI), milk))
    }

    @Test fun samsClubLineNeverSplitsEvenForMeat() {
        // A Sam's purchase is bulk stock for the freezer, bought on the big
        // trip; the category does not move it to the top-up.
        val pork = item("pork-tenderloin", "Meat", Store.SAMS_CLUB)
        assertFalse(Perishables.isTopUpLine(line("pork-tenderloin", Store.SAMS_CLUB), pork))
    }

    @Test fun reroutedPerishableLineSplits() {
        // The gate rerouted this Sam's item to Aldi: it is bought fresh by
        // quantity, so week 2's share waits for the top-up like any fresh meat.
        val pork = item("pork-tenderloin", "Meat", Store.SAMS_CLUB)
        assertTrue(Perishables.isTopUpLine(line("pork-tenderloin", Store.ALDI), pork))
    }

    @Test fun shelfStableLineNeverSplits() {
        val pasta = item("spaghetti", "Dry and canned", Store.ALDI)
        assertFalse(Perishables.isTopUpLine(line("spaghetti", Store.ALDI), pasta))
    }

    @Test fun unknownItemNeverSplits() {
        assertFalse(Perishables.isTopUpLine(line("mystery", Store.ALDI), null))
    }
}
