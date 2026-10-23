package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StorePrice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-7: the dedup merge of the reconciled ran-out set into a
 * planned week's lines. Pins Battles's rules by name: a ran-out staple
 * is always listed, a ran-out ingredient only when a planned recipe uses
 * it, nothing appears twice, and merged quantities are the maximum of
 * the two reasons, never the sum.
 */
class ReconciliationMergeTest {

    private fun item(
        id: String, name: String, store: Store, packAmount: Double, unit: MeasureUnit,
        packPrice: Int, alternates: List<StorePrice> = emptyList(),
    ) = PriceItem(
        id = id, name = name, category = "Test", homeStore = store, packDescription = "pack",
        packQuantity = Quantity(packAmount, unit), packPriceCents = packPrice,
        pricePerUnitCents = packPrice / packAmount,
        checkedDate = "2026-10-22", source = "test", alternates = alternates,
    )

    private val tenderloin = item(
        "pork-tenderloin", "Pork tenderloin", Store.SAMS_CLUB, 9.0, MeasureUnit.POUND, 2168,
        alternates = listOf(StorePrice(Store.ALDI, 297.0)),
    )
    private val spaghetti = item("spaghetti", "Spaghetti", Store.ALDI, 32.0, MeasureUnit.OUNCE, 209)
    private val eggs = item("eggs", "Eggs", Store.ALDI, 12.0, MeasureUnit.COUNT, 185)
    private val book = listOf(tenderloin, spaghetti, eggs)

    private fun line(item: PriceItem, needed: Quantity, packs: Int, price: Int) = GroceryLine(
        itemId = item.id, displayName = item.name, store = item.homeStore,
        needed = needed, packsToBuy = packs, estimatedPackPriceCents = price,
    )

    private fun stapleOut(item: PriceItem) = ReconciledOut(
        itemKey = "item:${item.id}", displayName = item.name, stapleSide = true,
        priceItemId = item.id,
        bulkItemId = item.takeIf { it.homeStore == Store.SAMS_CLUB }?.id,
    )

    private fun ingredientOut(item: PriceItem) = ReconciledOut(
        itemKey = "item:${item.id}", displayName = item.name, stapleSide = false,
        priceItemId = item.id,
    )

    private fun merge(
        lines: List<GroceryLine>,
        outs: List<ReconciledOut>,
        plannedItemIds: Set<String> = emptySet(),
        plannedNeedsBase: Map<String, Double> = emptyMap(),
        gateMet: Boolean = true,
    ) = ReconciliationMerge.merge(
        lines = lines,
        resolvedOuts = outs,
        priceItems = book,
        plannedItemIds = plannedItemIds,
        plannedNeedsBase = plannedNeedsBase,
        samsGateMet = gateMet,
        samsMonthlySavingsCents = if (gateMet) 505 else 0,
    )

    @Test fun ranOutStapleIsAlwaysListedEvenWhenNoRecipeNeedsIt() {
        val result = merge(
            lines = listOf(line(spaghetti, Quantity.ounces(16.0), packs = 1, price = 209)),
            outs = listOf(stapleOut(tenderloin)),
        )
        val added = result.lines.single { it.itemId == "pork-tenderloin" }
        assertEquals(1, added.packsToBuy)
        assertEquals(2168, added.estimatedPackPriceCents)
        assertTrue(added.reconciliationAdded)
        assertEquals(2168, result.cashDeltaCents)
        assertEquals(listOf("Pork tenderloin"), result.addedNames)
        assertEquals(2, result.lines.size)
    }

    @Test fun ranOutIngredientIsListedOnlyWhenAPlannedRecipeUsesIt() {
        // Not used by any planned dinner: stays off the list.
        val unused = merge(
            lines = listOf(line(spaghetti, Quantity.ounces(16.0), packs = 1, price = 209)),
            outs = listOf(ingredientOut(eggs)),
        )
        assertTrue(unused.lines.none { it.itemId == "eggs" })
        assertEquals(0, unused.cashDeltaCents)

        // Used by a planned dinner but unlisted (covered at plan time):
        // the planned need is bought, once.
        val used = merge(
            lines = listOf(line(spaghetti, Quantity.ounces(16.0), packs = 1, price = 209)),
            outs = listOf(ingredientOut(eggs)),
            plannedItemIds = setOf("eggs"),
            plannedNeedsBase = mapOf("eggs" to 6.0),
        )
        val eggsLine = used.lines.single { it.itemId == "eggs" }
        assertEquals(1, eggsLine.packsToBuy)
        assertEquals(185, eggsLine.estimatedPackPriceCents)
        assertTrue(eggsLine.reconciliationAdded)
        assertEquals(185, used.cashDeltaCents)
    }

    @Test fun alreadyListedItemAppearsOnceWithThePlanQuantityUntouched() {
        val planLine = line(spaghetti, Quantity.ounces(16.0), packs = 1, price = 209)
        val result = merge(
            lines = listOf(planLine),
            outs = listOf(ingredientOut(spaghetti)),
            plannedItemIds = setOf("spaghetti"),
            plannedNeedsBase = mapOf("spaghetti" to 16.0),
        )
        assertEquals(listOf(planLine), result.lines)
        assertEquals(0, result.cashDeltaCents)
    }

    @Test fun stapleMergeTopsQuantityToTheMaxNeverTheSum() {
        // Gate off: the plan's tenderloin need (2 lb) was rerouted to
        // Aldi by weight at 297c/lb (594c). The staple reason wants one
        // full 9 lb pack: the merged quantity is 9 lb (the max), priced
        // the same rerouted way, never 11 lb (the sum).
        val rerouted = GroceryLine(
            itemId = "pork-tenderloin", displayName = "Pork tenderloin", store = Store.ALDI,
            needed = Quantity.pounds(2.0), packsToBuy = 1, estimatedPackPriceCents = 594,
        )
        val result = merge(
            lines = listOf(rerouted),
            outs = listOf(stapleOut(tenderloin)),
            gateMet = false,
        )
        assertEquals(1, result.lines.size)
        val merged = result.lines.single()
        assertEquals(9.0, merged.needed.amount, 0.001)
        assertEquals(9 * 297, merged.estimatedPackPriceCents)
        assertEquals(9 * 297 - 594, result.cashDeltaCents)
        assertTrue(merged.reconciliationAdded)
        // A top-up is not an addition.
        assertTrue(result.addedNames.isEmpty())
    }

    @Test fun stapleAlreadyListedAtFullPackStandsUntouched() {
        val planLine = line(tenderloin, Quantity.pounds(9.0), packs = 1, price = 2168)
        val result = merge(
            lines = listOf(planLine),
            outs = listOf(stapleOut(tenderloin)),
        )
        assertEquals(listOf(planLine), result.lines)
        assertEquals(0, result.cashDeltaCents)
    }

    @Test fun unpricedStapleNeverBecomesALine() {
        val result = merge(
            lines = emptyList(),
            outs = listOf(
                ReconciledOut(
                    itemKey = "pantry:olive oil", displayName = "Olive oil",
                    stapleSide = true, pantryStapleName = "Olive oil",
                ),
            ),
        )
        assertTrue(result.lines.isEmpty())
        assertEquals(0, result.cashDeltaCents)
    }
}
