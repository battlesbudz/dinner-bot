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

    /**
     * Ids of priced items already on hand because an owned pantry entry
     * matches them by name: "Potatoes" in the pantry covers the priced item
     * "Russet potatoes". This is the costing layer's one link between the
     * name-based pantry and the price book, so the week plan total and the
     * shopping list can never disagree about an item being already home.
     *
     * The match is deliberately conservative. Names are normalized (case
     * folded, punctuation treated as a word break, simple plurals folded to
     * singular) and a pantry entry matches an item when the entry's words
     * are the item name's words or a whole-word ending of them: "Potatoes"
     * and "Potato" both match "Russet potatoes", while "Pot" (a partial
     * word) and "Russet" (not the ending) never do.
     */
    fun pantryCoveredItemIds(
        pantryOwnedNames: Collection<String>,
        items: Collection<PriceItem>,
    ): Set<String> {
        val pantryWords = pantryOwnedNames.map { normalizedWords(it) }.filter { it.isNotEmpty() }
        if (pantryWords.isEmpty()) return emptySet()
        return items.filter { item ->
            val itemWords = normalizedWords(item.name)
            itemWords.isNotEmpty() && pantryWords.any { words -> endsWithWords(itemWords, words) }
        }.map { it.id }.toSet()
    }

    /** True when [words] ends with [suffix] as whole words; a full-length suffix is an exact match. */
    private fun endsWithWords(words: List<String>, suffix: List<String>): Boolean {
        if (suffix.size > words.size) return false
        return words.subList(words.size - suffix.size, words.size) == suffix
    }

    /** Lowercased words of a name, each folded from a simple plural to its singular. */
    private fun normalizedWords(name: String): List<String> =
        name.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }
            .map { singularOf(it) }

    /**
     * Fold a simple plural to its singular: "potatoes" -> "potato",
     * "onions" -> "onion". Conservative on purpose: only a trailing "oes"
     * or a single trailing "s" (never "ss", so "glass" is untouched).
     */
    private fun singularOf(word: String): String = when {
        word.length > 3 && word.endsWith("oes") -> word.dropLast(2)
        word.length > 1 && word.endsWith("s") && !word.endsWith("ss") -> word.dropLast(1)
        else -> word
    }

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
        /** Priced items whose need was covered by a matching owned pantry entry. */
        val pantryCoveredItemIds: Set<String> = emptySet(),
    )

    /**
     * Price one set of needs into grocery lines.
     *
     * @param stockOnHand tracked Sam's bulk stock, item id -> quantity on hand
     *   (quantities may be in any unit of the right dimension).
     * @param pantryCoveredItemIds priced items an owned pantry entry matches
     *   (see [pantryCoveredItemIds] for the matching rule). A covered item is
     *   already home, so it gets the same on-hand treatment as Sam's stock:
     *   no shopping line and $0 cash, with its portion cost still reported
     *   as information. Sam's bulk stock behavior is unchanged.
     */
    fun costNeeds(
        needs: List<ItemNeed>,
        stockOnHand: Map<String, Quantity>,
        pantryCoveredItemIds: Set<String> = emptySet(),
    ): CostedWeek {
        val gate = evaluateSamsGate(needs)
        val lines = mutableListOf<GroceryLine>()
        val stockCovered = mutableSetOf<String>()
        val pantryCovered = mutableSetOf<String>()
        var cash = 0
        var foodCost = 0

        for (need in needs) {
            val item = need.item
            val portionCost = (need.neededInPackUnit * item.perUnitCents).roundToInt()
            foodCost += portionCost

            if (item.id in pantryCoveredItemIds) {
                pantryCovered += item.id
                continue
            }

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
            pantryCoveredItemIds = pantryCovered,
        )
    }

    /**
     * What is left of a bought Sam's Club pack after this week's dinners use
     * what they need from it (Phase V2-2 freeze guidance): the packs bought
     * for [line] minus the line's needed amount, in the item's pack unit.
     * Null when the line is not a Sam's purchase of a Sam's item, or when the
     * packs are used up exactly. The line's needed amount is what remained
     * after home stock was drawn, so this is the NEW leftover the purchase
     * creates, the amount the freeze note is about.
     */
    fun buyLeftover(line: GroceryLine, item: PriceItem): Quantity? {
        if (line.store != Store.SAMS_CLUB || item.homeStore != Store.SAMS_CLUB) return null
        if (line.packsToBuy <= 0) return null
        val boughtBase = item.packQuantity.toBase() * line.packsToBuy
        val leftoverBase = boughtBase - line.needed.toBase()
        if (leftoverBase <= EPSILON) return null
        return Quantity(leftoverBase / item.packQuantity.unit.toBaseFactor, item.packQuantity.unit)
    }

    /** Price a full set of recipes for one week. Convenience over [aggregateNeeds] + [costNeeds]. */
    fun costRecipes(
        recipes: List<Recipe>,
        forServings: Int,
        itemsById: Map<String, PriceItem>,
        stockOnHand: Map<String, Quantity>,
        pantryCoveredItemIds: Set<String> = emptySet(),
    ): CostedWeek {
        val (needs, _) = aggregateNeeds(recipes, forServings, itemsById)
        return costNeeds(needs, stockOnHand, pantryCoveredItemIds)
    }
}
