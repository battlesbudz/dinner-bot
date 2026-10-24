package com.battlesbudz.dinnerbot.feature.grocerylist

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.ReconciledOut

/**
 * The Sunday reconciliation dedup merge (Phase V2-7, scope B4 step 3):
 * the reconciled ran-out set merges into a planned week's lines, so the
 * list is built once, from the plan and the corrected state together.
 *
 * Battles's rules, exactly:
 * - A ran-out STAPLE is always on the list, whether or not a planned
 *   recipe uses it: one pack at its home store (or its gate-routed
 *   store, priced by the same Costing path as any planned line).
 * - A ran-out INGREDIENT is on the list only if a planned dinner uses
 *   it, and then it is the recipe's line, not a second line.
 * - Nothing appears twice: an item the plan already lists keeps its one
 *   line. When both reasons want the item, the quantity is the MAXIMUM
 *   of the two needs, never the sum: the plan's need stands, topped up
 *   to one full pack when the plan needed less than a pack.
 *
 * Lines this merge adds or tops up carry [GroceryLine.reconciliationAdded].
 * Working in lines (not plans) keeps the merge usable for both weeks of
 * a fortnight, including week 2's top-up after its lighter check.
 */
object ReconciliationMerge {

    data class Result(
        val lines: List<GroceryLine>,
        /** Change to the week's cash total, cents (new line cash minus old). */
        val cashDeltaCents: Int,
        /** Display names of the lines this merge added (not merely topped up). */
        val addedNames: List<String>,
    )

    fun merge(
        lines: List<GroceryLine>,
        resolvedOuts: List<ReconciledOut>,
        priceItems: List<PriceItem>,
        /** Priced item ids the planned (cooking) dinners use. */
        plannedItemIds: Set<String>,
        /** Aggregated planned need per priced item, in base units (Costing.aggregateNeeds). */
        plannedNeedsBase: Map<String, Double>,
        /** The Sam's gate the week was planned under; its routing is kept for merged lines. */
        samsGateMet: Boolean,
        samsMonthlySavingsCents: Int,
    ): Result {
        val itemsById = priceItems.associateBy { it.id }
        val gate = Costing.SamsGateResult(
            projectedMonthlySavingsCents = samsMonthlySavingsCents,
            useSams = samsGateMet,
            savingsByItemCents = emptyMap(),
        )
        val byItem = lines.associateBy { it.itemId }.toMutableMap()
        val order = lines.map { it.itemId }.toMutableList()
        var delta = 0
        val added = mutableListOf<String>()

        fun cashOf(line: GroceryLine): Int = line.packsToBuy * line.estimatedPackPriceCents

        fun costSingleLine(item: PriceItem, needBase: Double): GroceryLine? =
            Costing.costNeeds(
                listOf(Costing.ItemNeed(item, needBase)),
                stockOnHand = emptyMap(),
                pantryCoveredItemIds = emptySet(),
                gateOverride = gate,
            ).lines.firstOrNull()

        for (out in resolvedOuts) {
            val itemId = out.priceItemId ?: continue // unpriced staples are never list lines
            val item = itemsById[itemId] ?: continue
            // A pantry-covered line (Phase RI-3) is not a buy line: the
            // item is on the list as already home, at $0. A confirmed
            // ran-out means it is NOT home, so the covered line must not
            // satisfy this merge — the item falls through to the add
            // rules below and its covered line is replaced by the buy
            // line they build.
            val existing = byItem[itemId]?.takeUnless { it.coveredByPantryName != null }
            if (existing != null) {
                // One line only. For a staple, top the need up to a full
                // pack when the plan needed less than one: the maximum of
                // the two reasons, never their sum. For an ingredient, the
                // plan's line IS the merged line, quantity untouched.
                if (!out.stapleSide) continue
                val packBase = item.packQuantity.toBase()
                if (existing.needed.toBase() >= packBase - EPSILON) continue
                val topped = costSingleLine(item, packBase) ?: continue
                val replacement = topped.copy(
                    checkedOff = existing.checkedOff,
                    reconciliationAdded = true,
                )
                byItem[itemId] = replacement
                delta += cashOf(replacement) - cashOf(existing)
                continue
            }
            when {
                out.stapleSide -> {
                    val line = costSingleLine(item, item.packQuantity.toBase()) ?: continue
                    val marked = line.copy(reconciliationAdded = true)
                    byItem[itemId] = marked
                    // A replaced covered line already holds this id's
                    // place in the order; never list the item twice.
                    if (itemId !in order) order += itemId
                    delta += cashOf(marked)
                    added += item.name
                }
                itemId in plannedItemIds -> {
                    // Planned but unlisted (stock or pantry covered it at
                    // plan time): it ran out, so the planned need is bought.
                    val needBase = plannedNeedsBase[itemId] ?: continue
                    if (needBase <= EPSILON) continue
                    val line = costSingleLine(item, needBase) ?: continue
                    val marked = line.copy(reconciliationAdded = true)
                    byItem[itemId] = marked
                    if (itemId !in order) order += itemId
                    delta += cashOf(marked)
                    added += item.name
                }
                // A ran-out ingredient no planned dinner uses is not bought
                // this week; it stays off the pantry-owned state, so the
                // week that does need it buys it then.
                else -> Unit
            }
        }

        return Result(
            lines = order.mapNotNull { byItem[it] },
            cashDeltaCents = delta,
            addedNames = added,
        )
    }

    private const val EPSILON = 1e-9
}
