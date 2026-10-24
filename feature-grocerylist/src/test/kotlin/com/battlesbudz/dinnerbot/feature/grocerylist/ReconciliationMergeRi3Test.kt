package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-3 meets the Sunday reconciliation (Phase V2-7): a
 * pantry-covered line is a display line, not a buy line. When a covered
 * item is confirmed ran out, the merge replaces the covered line with a
 * real buy line, exactly once, and the week's cash rises by the full
 * line — before RI-3 the item silently never got bought.
 */
class ReconciliationMergeRi3Test {

    private val eggs = PriceItem(
        id = "eggs", name = "Eggs", category = "Test", homeStore = Store.ALDI,
        packDescription = "12 ct", packQuantity = Quantity(12.0, MeasureUnit.COUNT),
        packPriceCents = 185, pricePerUnitCents = 15.4,
        checkedDate = "2026-10-24", source = "test",
    )
    private val book = listOf(eggs)

    private val coveredEggs = GroceryLine(
        itemId = "eggs", displayName = "Eggs", store = Store.ALDI,
        needed = Quantity(6.0, MeasureUnit.COUNT), packsToBuy = 0,
        estimatedPackPriceCents = 185, portionCostCents = 92,
        coveredByPantryName = "Eggs",
    )

    private fun stapleOut() = ReconciledOut(
        itemKey = "item:eggs", displayName = "Eggs", stapleSide = true,
        priceItemId = "eggs", pantryStapleName = "Eggs",
    )

    private fun ingredientOut() = ReconciledOut(
        itemKey = "item:eggs", displayName = "Eggs", stapleSide = false,
        priceItemId = "eggs",
    )

    private fun merge(
        lines: List<GroceryLine>,
        outs: List<ReconciledOut>,
        plannedItemIds: Set<String> = emptySet(),
        plannedNeedsBase: Map<String, Double> = emptyMap(),
    ) = ReconciliationMerge.merge(
        lines = lines, resolvedOuts = outs, priceItems = book,
        plannedItemIds = plannedItemIds, plannedNeedsBase = plannedNeedsBase,
        samsGateMet = false, samsMonthlySavingsCents = 0,
    )

    @Test fun coveredStapleConfirmedOutIsReplacedByOneBuyLine() {
        val result = merge(listOf(coveredEggs), listOf(stapleOut()))
        val eggsLines = result.lines.filter { it.itemId == "eggs" }
        assertEquals(1, eggsLines.size)
        val bought = eggsLines.single()
        assertNull(bought.coveredByPantryName)
        assertEquals(1, bought.packsToBuy)
        assertTrue(bought.reconciliationAdded)
        // The covered line cost $0, so the whole line is new cash.
        assertEquals(185, result.cashDeltaCents)
        assertEquals(listOf("Eggs"), result.addedNames)
    }

    @Test fun coveredIngredientUsedByAPlannedDinnerIsBought() {
        val result = merge(
            listOf(coveredEggs), listOf(ingredientOut()),
            plannedItemIds = setOf("eggs"),
            plannedNeedsBase = mapOf("eggs" to 6.0),
        )
        val eggsLines = result.lines.filter { it.itemId == "eggs" }
        assertEquals(1, eggsLines.size)
        assertNull(eggsLines.single().coveredByPantryName)
        assertEquals(1, eggsLines.single().packsToBuy)
        assertEquals(185, result.cashDeltaCents)
    }

    @Test fun coveredLineWithNoRanOutStaysCovered() {
        val result = merge(listOf(coveredEggs), emptyList())
        assertEquals(listOf(coveredEggs), result.lines)
        assertEquals(0, result.cashDeltaCents)
        assertTrue(result.addedNames.isEmpty())
    }
}
