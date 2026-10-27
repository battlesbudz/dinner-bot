package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.MealPlan
import com.battlesbudz.dinnerbot.core.model.Perishables
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe

/**
 * Builds the shopping list for a plan: aggregates the planned dinners'
 * ingredients scaled to the household size, lets tracked bulk stock
 * cover what it can ($0 cash), splits the rest into per-store lines priced
 * from the price book. The pantry staples are priced book items like
 * everything else (v0.4.3), so an unowned staple appears as a normal
 * priced line. A priced item an owned pantry entry matches by name
 * (see [Costing.pantryCoveredItemIds]) is already home: since Phase RI-3
 * it stays ON the list as a crossed-out $0 line naming the covering
 * pantry entry (the user's weekly-list ruling), unless the user uncrossed
 * it for this week, when it is a buy line at book price. An ingredient
 * no book item prices (an imported recipe's as-written line) appears
 * with a labeled placeholder guess in place of a price. The decisions
 * all live in Costing, so this list and the plan total are computed
 * from the same costing and cannot disagree.
 */
object GroceryListBuilder {

    data class BuiltList(
        val lines: List<GroceryLine>,
        /** Cash per store id (see Store). */
        val cashByStoreCents: Map<String, Int>,
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
        uncrossedItemIds: Set<String> = emptySet(),
    ): BuiltList {
        val itemsById = priceItems.associateBy { it.id }
        val dinners = plan.nights.mapNotNull { recipesById[it.recipeId] }
        val costed = Costing.costRecipes(
            dinners,
            servings,
            itemsById,
            stockOnHand,
            pantryCoveredItemIds = Costing.pantryCoveredItemIds(pantryOwnedNames, priceItems),
            pantryOwnedNames = pantryOwnedNames,
            uncrossedItemIds = uncrossedItemIds,
            forList = true,
        )
        val byStore = costed.lines.groupBy { it.store }
            .mapValues { (_, lines) -> lines.sumOf { lineCashCents(it) } }
        return BuiltList(
            lines = costed.lines,
            cashByStoreCents = byStore,
            totalCashCents = costed.cashNeededCents,
            foodCostCents = costed.foodCostCents,
        )
    }

    /**
     * Estimated cash for one line: packs times the per-pack estimate.
     * A pantry-covered line (Phase RI-3) is $0 — it is already home. A
     * guess-priced item's price is its book price, so it needs no
     * special case (the user's correction, 2026-10-24).
     */
    fun lineCashCents(line: GroceryLine): Int =
        if (line.coveredByPantryName != null) {
            0
        } else {
            line.packsToBuy * line.estimatedPackPriceCents
        }

    /**
     * The two shopping sections of a two-week plan (Phase V2-3): the one
     * big trip and week 2's fresh top-up. Every line of both weeks lands
     * in exactly one section, and the section cashes sum to the two weeks'
     * combined cash.
     */
    data class FortnightSections(
        /** All of week 1, plus week 2's shelf-stable, frozen, and bulk stock lines. */
        val bigTripLines: List<GroceryLine>,
        /** Week 2's perishable lines ([Perishables.isTopUpLine]), bought at the start of week 2. */
        val topUpLines: List<GroceryLine>,
        val bigTripCashCents: Int,
        val topUpCashCents: Int,
    ) {
        val totalCashCents: Int get() = bigTripCashCents + topUpCashCents
    }

    /**
     * Split two weeks' lines into the big trip and the fresh top-up. The
     * rule is [Perishables]: category-based (Produce, Dairy and eggs, and
     * Meat are perishable), with a bulk stock purchase never
     * splitting. Week 1 lines never split: the big trip is when week 1 is
     * bought anyway.
     */
    fun splitFortnight(
        week1Lines: List<GroceryLine>,
        week2Lines: List<GroceryLine>,
        priceItems: List<PriceItem>,
    ): FortnightSections {
        val itemsById = priceItems.associateBy { it.id }
        // Reconciliation-added week 2 lines (Phase V2-7) shop with the
        // top-up: the big trip predates the week 2 check that added them.
        fun isTopUp(line: GroceryLine): Boolean =
            line.reconciliationAdded || Perishables.isTopUpLine(line, itemsById[line.itemId])
        val topUp = week2Lines.filter { isTopUp(it) }
        val bigTrip = week1Lines + week2Lines.filterNot { isTopUp(it) }
        return FortnightSections(
            bigTripLines = bigTrip,
            topUpLines = topUp,
            bigTripCashCents = bigTrip.sumOf { lineCashCents(it) },
            topUpCashCents = topUp.sumOf { lineCashCents(it) },
        )
    }
}
