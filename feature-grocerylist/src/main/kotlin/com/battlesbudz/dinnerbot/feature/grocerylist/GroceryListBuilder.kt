package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MealPlan
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.Store

/**
 * Builds the shopping list for a plan: aggregates the planned dinners'
 * ingredients scaled to the household size, lets tracked Sam's bulk stock
 * cover what it can ($0 cash), splits the rest into per-store lines priced
 * from the price book. Pantry staples never appear: they are not priced.
 * A priced item an owned pantry entry matches by name
 * (see [Costing.pantryCoveredItemIds]) is already home and never appears
 * either; the decision lives in Costing, so this list and the plan total
 * are computed from the same covered set and cannot disagree.
 */
object GroceryListBuilder {

    data class BuiltList(
        val lines: List<GroceryLine>,
        val cashByStoreCents: Map<Store, Int>,
        val totalCashCents: Int,
        val foodCostCents: Int,
    )

    fun build(
        plan: MealPlan,
        recipesById: Map<String, Recipe>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
        servings: Int,
        pantryOwnedNames: Set<String> = emptySet(),
    ): BuiltList {
        val itemsById = priceItems.associateBy { it.id }
        val dinners = plan.nights.mapNotNull { recipesById[it.recipeId] }
        val costed = Costing.costRecipes(
            dinners,
            servings,
            itemsById,
            stockOnHand,
            pantryCoveredItemIds = Costing.pantryCoveredItemIds(pantryOwnedNames, priceItems),
        )
        val byStore = costed.lines.groupBy { it.store }
            .mapValues { (_, lines) -> lines.sumOf { it.packsToBuy * it.estimatedPackPriceCents } }
        return BuiltList(
            lines = costed.lines,
            cashByStoreCents = byStore,
            totalCashCents = costed.cashNeededCents,
            foodCostCents = costed.foodCostCents,
        )
    }

    /** Estimated cash for one line: packs times the per-pack estimate. */
    fun lineCashCents(line: GroceryLine): Int = line.packsToBuy * line.estimatedPackPriceCents
}
