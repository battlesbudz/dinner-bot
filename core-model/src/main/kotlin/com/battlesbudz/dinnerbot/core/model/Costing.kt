package com.battlesbudz.dinnerbot.core.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Shared costing engine for the planner and the grocery list. Pure functions:
 * no Android, no database, fully unit-testable on the JVM.
 *
 * Locked household rules (Battles 2026-10-21/22) implemented here:
 * - Ground beef is always substituted with ground turkey (see [substituteItemId]).
 * - Sam's Club bulk packs charge their FULL price in the week they are bought
 *   and $0 in weeks where tracked bulk stock covers the need. Portion cost is
 *   information only; the budget only ever sees cash.
 * - Sam's Club items are bought at Sam's only when the projected monthly
 *   savings reach [SAMS_MONTHLY_GATE_CENTS]; otherwise the need reroutes to
 *   the cheapest regular store that sells it.
 */
object Costing {
    const val SAMS_MONTHLY_GATE_CENTS: Int = 1500
    const val WEEKS_PER_MONTH: Double = 4.0
    private const val EPSILON = 1e-9

    /** Household substitution: any beef item id resolves to ground turkey. */
    fun substituteItemId(itemId: String): String =
        if (itemId.contains("beef", ignoreCase = true)) HouseholdRules.SUBSTITUTE_ITEM_ID else itemId

    /** One aggregated need for one price item, expressed in base units. */
    data class ItemNeed(
        val item: PriceItem,
        val neededBase: Double,
    ) {
        /** The same need converted into the item's pack unit (the per-unit price basis). */
        val neededInPackUnit: Double get() = neededBase / item.packQuantity.unit.toBaseFactor
    }

    /**
     * Aggregate the priced ingredients of [recipes], each scaled to
     * [forServings], into per-item needs. Pantry ingredients are returned
     * separately by name (they are never priced).
     */
    fun aggregateNeeds(
        recipes: List<Recipe>,
        forServings: Int,
        itemsById: Map<String, PriceItem>,
    ): Pair<List<ItemNeed>, List<String>> {
        val baseByItem = linkedMapOf<String, Double>()
        val pantryNames = linkedSetOf<String>()
        for (recipe in recipes) {
            for (ingredient in recipe.scaledIngredients(forServings)) {
                val pantry = ingredient.pantryItem
                if (pantry != null) {
                    pantryNames += pantry
                    continue
                }
                val rawId = ingredient.priceItemId ?: continue
                val id = substituteItemId(rawId)
                val item = itemsById[id] ?: itemsById[rawId] ?: continue
                baseByItem[item.id] = (baseByItem[item.id] ?: 0.0) + ingredient.quantity.toBase()
            }
        }
        val needs = baseByItem.mapNotNull { (id, base) ->
            itemsById[id]?.let { ItemNeed(it, base) }
        }
        return needs to pantryNames.toList()
    }

    /** Result of the Sam's Club $15/month gate for one set of weekly needs. */
    data class SamsGateResult(
        /** Projected savings this month, in cents, summed over Sam's items with a known alternate. */
        val projectedMonthlySavingsCents: Int,
        /** True when the gate is met and Sam's items are planned at Sam's Club. */
        val useSams: Boolean,
        /** Per-item projected monthly savings in cents (only items with a known alternate). */
        val savingsByItemCents: Map<String, Int>,
    )

    /**
     * Gate math (Battles): savings per item = (cheapest regular per-unit price
     * minus Sam's per-unit price) x quantity actually used that month. Monthly
     * use is projected from this week's use x [WEEKS_PER_MONTH]. An item with
     * no known regular alternate proves no savings and contributes $0, which
     * means ground turkey (cheaper at Walmart) can never carry the gate.
     */
    fun evaluateSamsGate(needs: List<ItemNeed>): SamsGateResult {
        val savings = linkedMapOf<String, Int>()
        for (need in needs) {
            val item = need.item
            if (item.homeStore != Store.SAMS_CLUB) continue
            val alternate = item.cheapestRegularAlternate() ?: continue
            val perUnitSaving = alternate.pricePerUnitCents - item.perUnitCents
            if (perUnitSaving <= 0.0) {
                savings[item.id] = 0
                continue
            }
            val monthlyUsed = need.neededInPackUnit * WEEKS_PER_MONTH
            savings[item.id] = (perUnitSaving * monthlyUsed).roundToInt()
        }
        val total = savings.values.sum()
        return SamsGateResult(
            projectedMonthlySavingsCents = total,
            useSams = total >= SAMS_MONTHLY_GATE_CENTS,
            savingsByItemCents = savings,
        )
    }

    /** A fully costed week: grocery lines plus the two headline numbers. */
    data class CostedWeek(
        val lines: List<GroceryLine>,
        /** Cash to hand over this week, across all stores. This is the budget number. */
        val cashNeededCents: Int,
        /** Attributed food cost of what the week's dinners actually use. Information only. */
        val foodCostCents: Int,
        val gate: SamsGateResult,
        /** Sam's items whose need was (partly) covered by bulk stock at home. */
        val stockCoveredItemIds: Set<String>,
    )

    /**
     * Price one set of needs into grocery lines.
     *
     * @param stockOnHand tracked Sam's bulk stock, item id -> quantity on hand
     *   (quantities may be in any unit of the right dimension).
     * @param pantryOwned pantry staple names currently marked owned; pantry
     *   ingredients are never priced, so they never produce lines.
     */
    fun costNeeds(
        needs: List<ItemNeed>,
        stockOnHand: Map<String, Quantity>,
    ): CostedWeek {
        val gate = evaluateSamsGate(needs)
        val lines = mutableListOf<GroceryLine>()
        val stockCovered = mutableSetOf<String>()
        var cash = 0
        var foodCost = 0

        for (need in needs) {
            val item = need.item
            val portionCost = (need.neededInPackUnit * item.perUnitCents).roundToInt()
            foodCost += portionCost

            var remainingBase = need.neededBase
            if (item.homeStore == Store.SAMS_CLUB) {
                val onHand = stockOnHand[item.id]
                if (onHand != null && onHand.toBase() > 0.0) {
                    val covered = minOf(remainingBase, onHand.toBase())
                    if (covered > EPSILON) stockCovered += item.id
                    remainingBase -= covered
                }
            }
            if (remainingBase <= EPSILON) continue

            val packBase = item.packQuantity.toBase()
            when {
                item.homeStore == Store.SAMS_CLUB && gate.useSams -> {
                    val packs = ceil(remainingBase / packBase - EPSILON).toInt().coerceAtLeast(1)
                    val lineCash = packs * item.packPriceCents
                    cash += lineCash
                    lines += GroceryLine(
                        itemId = item.id,
                        displayName = item.name,
                        store = Store.SAMS_CLUB,
                        needed = Quantity(remainingBase / item.packQuantity.unit.toBaseFactor, item.packQuantity.unit),
                        packsToBuy = packs,
                        estimatedPackPriceCents = item.packPriceCents,
                        portionCostCents = portionCost,
                    )
                }
                item.homeStore == Store.SAMS_CLUB -> {
                    // Gate not met: reroute to the cheapest regular store, bought
                    // by quantity at that store's per-unit price. No Sam's trip.
                    val alternate = item.cheapestRegularAlternate()
                    if (alternate == null) {
                        // No known alternate: fall back to the home store pack math.
                        val packs = ceil(remainingBase / packBase - EPSILON).toInt().coerceAtLeast(1)
                        val lineCash = packs * item.packPriceCents
                        cash += lineCash
                        lines += GroceryLine(
                            itemId = item.id,
                            displayName = item.name,
                            store = item.homeStore,
                            needed = Quantity(remainingBase / item.packQuantity.unit.toBaseFactor, item.packQuantity.unit),
                            packsToBuy = packs,
                            estimatedPackPriceCents = item.packPriceCents,
                            portionCostCents = portionCost,
                        )
                    } else {
                        val remainingInPackUnit = remainingBase / item.packQuantity.unit.toBaseFactor
                        val lineCash = (remainingInPackUnit * alternate.pricePerUnitCents).roundToInt()
                        cash += lineCash
                        lines += GroceryLine(
                            itemId = item.id,
                            displayName = item.name,
                            store = alternate.store,
                            needed = Quantity(remainingInPackUnit, item.packQuantity.unit),
                            packsToBuy = 1,
                            estimatedPackPriceCents = lineCash,
                            portionCostCents = (remainingInPackUnit * alternate.pricePerUnitCents).roundToInt(),
                        )
                    }
                }
                else -> {
                    val packs = ceil(remainingBase / packBase - EPSILON).toInt().coerceAtLeast(1)
                    val lineCash = packs * item.packPriceCents
                    cash += lineCash
                    lines += GroceryLine(
                        itemId = item.id,
                        displayName = item.name,
                        store = item.homeStore,
                        needed = Quantity(remainingBase / item.packQuantity.unit.toBaseFactor, item.packQuantity.unit),
                        packsToBuy = packs,
                        estimatedPackPriceCents = item.packPriceCents,
                        portionCostCents = portionCost,
                    )
                }
            }
        }
        return CostedWeek(
            lines = lines,
            cashNeededCents = cash,
            foodCostCents = foodCost,
            gate = gate,
            stockCoveredItemIds = stockCovered,
        )
    }

    /** Price a full set of recipes for one week. Convenience over [aggregateNeeds] + [costNeeds]. */
    fun costRecipes(
        recipes: List<Recipe>,
        forServings: Int,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
    ): CostedWeek {
        val (needs, _) = aggregateNeeds(recipes, forServings, itemsById)
        return costNeeds(needs, stockOnHand)
    }
}
