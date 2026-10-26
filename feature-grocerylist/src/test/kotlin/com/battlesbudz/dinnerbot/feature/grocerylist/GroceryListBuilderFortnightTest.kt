package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two-week list split (Phase V2-3): week 2's perishables (Produce,
 * Dairy and eggs, Meat categories) wait for the fresh top-up; week 2's
 * shelf-stable and frozen items and every Sam's stock purchase ride the
 * big trip with all of week 1.
 */
class GroceryListBuilderFortnightTest {

    private fun item(id: String, category: String, store: String, freezeNote: String? = null) = PriceItem(
        id = id, name = id, category = category, homeStore = store, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        checkedDate = "2026-10-22", source = "test", freezeNote = freezeNote,
    )

    private fun line(itemId: String, store: String, packs: Int = 1, price: Int = 100) = GroceryLine(
        itemId = itemId, displayName = itemId, store = store,
        needed = Quantity(1.0, MeasureUnit.POUND), packsToBuy = packs, estimatedPackPriceCents = price,
    )

    private val book = listOf(
        item("milk", "Dairy and eggs", "aldi"),
        item("russet-potatoes", "Produce", "aldi"),
        item("chicken-breast", "Meat", "aldi"),
        item("spaghetti", "Dry and canned", "aldi"),
        item("frozen-broccoli", "Frozen", "walmart"),
        item("pork-tenderloin", "Meat", "sams-club", freezeNote = "Freeze the rest."),
    )

    @Test fun week2PerishablesGoToTopUpAndShelfStableStaysOnBigTrip() {
        val week1 = listOf(line("milk", "aldi"), line("spaghetti", "aldi"))
        val week2 = listOf(
            line("milk", "aldi"),
            line("russet-potatoes", "aldi"),
            line("chicken-breast", "aldi"),
            line("spaghetti", "aldi"),
            line("frozen-broccoli", "walmart"),
        )
        val sections = GroceryListBuilder.splitFortnight(week1, week2, book)
        assertEquals(
            setOf("milk", "russet-potatoes", "chicken-breast"),
            sections.topUpLines.map { it.itemId }.toSet(),
        )
        // Big trip: all of week 1 (including its perishable milk, bought for
        // week 1 anyway) plus week 2's shelf-stable and frozen lines.
        assertEquals(
            listOf("milk", "spaghetti", "spaghetti", "frozen-broccoli"),
            sections.bigTripLines.map { it.itemId },
        )
        assertEquals(week1.size + 2, sections.bigTripLines.size)
    }

    @Test fun bulkPackPurchaseRidesTheBigTripEvenThoughItIsMeat() {
        val week2 = listOf(line("pork-tenderloin", "sams-club", packs = 1, price = 2168))
        val sections = GroceryListBuilder.splitFortnight(emptyList(), week2, book)
        assertTrue(sections.topUpLines.isEmpty())
        assertEquals(listOf("pork-tenderloin"), sections.bigTripLines.map { it.itemId })
    }

    @Test fun freshMeatLineAtAnotherStoreSplitsToTopUp() {
        // A plain (non-bulk) meat line shops with the week 2 top-up.
        val week2 = listOf(line("chicken-breast", "walmart"))
        val sections = GroceryListBuilder.splitFortnight(emptyList(), week2, book)
        assertEquals(listOf("chicken-breast"), sections.topUpLines.map { it.itemId })
    }

    @Test fun sectionCashesSumToTheCombinedTotal() {
        val week1 = listOf(line("milk", "aldi", price = 250), line("spaghetti", "aldi", packs = 2, price = 209))
        val week2 = listOf(line("milk", "aldi", price = 250), line("spaghetti", "aldi", price = 209))
        val sections = GroceryListBuilder.splitFortnight(week1, week2, book)
        assertEquals(250, sections.topUpCashCents)
        assertEquals(250 + 2 * 209 + 209, sections.bigTripCashCents)
        assertEquals(
            week1.sumOf { it.packsToBuy * it.estimatedPackPriceCents } +
                week2.sumOf { it.packsToBuy * it.estimatedPackPriceCents },
            sections.totalCashCents,
        )
    }

    @Test fun everyLineLandsInExactlyOneSection() {
        val week1 = listOf(line("milk", "aldi"), line("pork-tenderloin", "sams-club"))
        val week2 = listOf(line("milk", "aldi"), line("spaghetti", "aldi"), line("frozen-broccoli", "walmart"))
        val sections = GroceryListBuilder.splitFortnight(week1, week2, book)
        assertEquals(week1.size + week2.size, sections.bigTripLines.size + sections.topUpLines.size)
    }
}
