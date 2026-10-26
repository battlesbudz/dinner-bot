package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-week freshness split (Phase V2-3) is category-based: Produce,
 * Dairy and eggs, and Meat are perishable; Dry and canned, Frozen, and
 * Bread keep for the fortnight. A bulk-pack line (the item carries a
 * freeze note) is a stock purchase and never splits, whatever its
 * category or the store it comes from (Phase C1).
 */
class PerishablesTest {

    private fun item(id: String, category: String, store: String, freezeNote: String? = null) = PriceItem(
        id = id, name = id, category = category, homeStore = store, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        checkedDate = "2026-10-22", source = "test", freezeNote = freezeNote,
    )

    private fun line(itemId: String, store: String) = GroceryLine(
        itemId = itemId, displayName = itemId, store = store,
        needed = Quantity(1.0, MeasureUnit.POUND), packsToBuy = 1, estimatedPackPriceCents = 100,
    )

    @Test fun perishableCategoriesAreFreshFood() {
        assertTrue(Perishables.isPerishable(item("russet-potatoes", "Produce", "aldi")))
        assertTrue(Perishables.isPerishable(item("milk", "Dairy and eggs", "aldi")))
        assertTrue(Perishables.isPerishable(item("chicken-breast", "Meat", "aldi")))
    }

    @Test fun shelfStableAndFrozenCategoriesAreNotPerishable() {
        assertFalse(Perishables.isPerishable(item("spaghetti", "Dry and canned", "aldi")))
        assertFalse(Perishables.isPerishable(item("frozen-broccoli", "Frozen", "walmart")))
        assertFalse(Perishables.isPerishable(item("white-bread", "Bread", "aldi")))
    }

    @Test fun week2PerishableLineAtARegularStoreIsTopUp() {
        val milk = item("milk", "Dairy and eggs", "aldi")
        assertTrue(Perishables.isTopUpLine(line("milk", "aldi"), milk))
    }

    @Test fun bulkPackLineNeverSplitsEvenForMeat() {
        // A bulk pack is stock for the freezer, bought on the big trip;
        // the category does not move it to the top-up.
        val pork = item("pork-tenderloin", "Meat", "sams-club", freezeNote = "Portioned at home.")
        assertFalse(Perishables.isTopUpLine(line("pork-tenderloin", "sams-club"), pork))
    }

    @Test fun freshMeatSplitsWhateverStoreItComesFrom() {
        // No freeze note: the item is bought fresh, so week 2's share
        // waits for the top-up like any fresh meat, at any store.
        val pork = item("pork-tenderloin", "Meat", "sams-club")
        assertTrue(Perishables.isTopUpLine(line("pork-tenderloin", "sams-club"), pork))
    }

    @Test fun shelfStableLineNeverSplits() {
        val pasta = item("spaghetti", "Dry and canned", "aldi")
        assertFalse(Perishables.isTopUpLine(line("spaghetti", "aldi"), pasta))
    }

    @Test fun unknownItemNeverSplits() {
        assertFalse(Perishables.isTopUpLine(line("mystery", "aldi"), null))
    }
}
