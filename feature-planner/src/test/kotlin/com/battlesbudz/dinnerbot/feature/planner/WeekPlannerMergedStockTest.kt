package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Codex review, PR #23: when a repriced fortnight preserves a
 * reconciliation-merged Sam's pack in week 1, the stock projection
 * handed to week 2 must count that pack. projectedStockAfter derives
 * stock from recipe needs and cannot see a pure restock line, so
 * [WeekPlanner.stockWithMergedPacks] folds it in.
 */
class WeekPlannerMergedStockTest {

    private val tenderloin = PriceItem(
        id = "pork-tenderloin", name = "Pork tenderloin", category = "Meat",
        homeStore = Store.SAMS_CLUB, packDescription = "9 lb pack",
        packQuantity = Quantity(9.0, MeasureUnit.POUND), packPriceCents = 2168,
        pricePerUnitCents = 240.9, checkedDate = "2026-10-23", source = "test",
    )
    private val salt = PriceItem(
        id = "salt", name = "Salt", category = "Dry and canned",
        homeStore = Store.ALDI, packDescription = "26 oz canister",
        packQuantity = Quantity(26.0, MeasureUnit.OUNCE), packPriceCents = 89,
        pricePerUnitCents = 3.4, checkedDate = "2026-10-23", source = "test",
        staple = true,
    )
    private val book = listOf(tenderloin, salt)

    private fun line(item: PriceItem, packs: Int, merged: Boolean) = GroceryLine(
        itemId = item.id, displayName = item.name, store = item.homeStore,
        needed = item.packQuantity, packsToBuy = packs,
        estimatedPackPriceCents = item.packPriceCents,
        reconciliationAdded = merged,
    )

    @Test fun mergedRestockPackJoinsTheProjection() {
        val result = WeekPlanner.stockWithMergedPacks(
            stock = emptyMap(),
            weekLines = listOf(line(tenderloin, packs = 1, merged = true)),
            costedLines = emptyList(),
            priceItems = book,
        )
        assertEquals(9.0, result.getValue("pork-tenderloin").amount, 0.001)
        assertEquals(MeasureUnit.POUND, result.getValue("pork-tenderloin").unit)
    }

    @Test fun mergedPackAddsToStockAlreadyProjected() {
        val result = WeekPlanner.stockWithMergedPacks(
            stock = mapOf("pork-tenderloin" to Quantity(3.0, MeasureUnit.POUND)),
            weekLines = listOf(line(tenderloin, packs = 1, merged = true)),
            costedLines = emptyList(),
            priceItems = book,
        )
        assertEquals(12.0, result.getValue("pork-tenderloin").amount, 0.001)
    }

    @Test fun costedLineIsNotCountedTwice() {
        val stock = mapOf("pork-tenderloin" to Quantity(3.0, MeasureUnit.POUND))
        val result = WeekPlanner.stockWithMergedPacks(
            stock = stock,
            weekLines = listOf(line(tenderloin, packs = 1, merged = true)),
            costedLines = listOf(line(tenderloin, packs = 1, merged = false)),
            priceItems = book,
        )
        assertSame(stock, result)
    }

    @Test fun plainAndNonSamsLinesChangeNothing() {
        val stock = emptyMap<String, Quantity>()
        val result = WeekPlanner.stockWithMergedPacks(
            stock = stock,
            weekLines = listOf(
                line(tenderloin, packs = 1, merged = false),
                line(salt, packs = 1, merged = true),
            ),
            costedLines = emptyList(),
            priceItems = book,
        )
        assertSame(stock, result)
    }
}
