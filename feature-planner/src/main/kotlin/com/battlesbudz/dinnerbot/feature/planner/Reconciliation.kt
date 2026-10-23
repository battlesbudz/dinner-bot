package com.battlesbudz.dinnerbot.feature.planner

import com.battlesbudz.dinnerbot.core.model.CookCheckoff
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.PossibleOut
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RanOutEvent
import com.battlesbudz.dinnerbot.core.model.RanOutKeys
import com.battlesbudz.dinnerbot.core.model.RanOutSource
import com.battlesbudz.dinnerbot.core.model.RanOutState
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.Store

/**
 * The Sunday reconciliation engine (Phase V2-7, scope B4). Pure and
 * deterministic, like the planner it gates: the same inputs always
 * produce the same session and outcome, which is what makes Battles's
 * flow testable end to end.
 *
 * The flow, in his order:
 * 0. During the week, the cook view records ingredient check-offs and
 *    ran-out marks ([RanOutEvent]s in state PENDING).
 * 1. Starting a new week opens reconciliation BEFORE planning, with three
 *    lists: what Andrea marked ran out, what the inference believes may
 *    also be out ([inferPossibleOuts]), and the pantry she owns.
 * 2. Only after reconciliation is the week planned, from the corrected
 *    pantry and stock state (the write-back lives in feature-pantry).
 * 3. The shopping list is built last, with the reconciled set merged in
 *    by the dedup rules (feature-grocerylist's ReconciliationMerge).
 *
 * A lighter check (lists 1 and 2 only) also gates week 2's fresh top-up
 * inside a two-week plan: reconciliation ran before week 1 was planned,
 * and it runs again, lightly, before week 2's top-up is shopped.
 */
object Reconciliation {

    /** Tolerance for stock arithmetic, matching Costing's own. */
    private const val EPSILON = 1e-9

    enum class CheckKind {
        /** The full three-list Sunday flow ahead of planning a new week. */
        FULL,

        /** Lists 1 and 2 only, ahead of week 2's fresh top-up shop. */
        TOP_UP,
    }

    /** One finished plan offered as a reconciliation source: its cooked nights feed the inference. */
    data class SourcePlan(
        val planId: String,
        val weekStart: String,
        val nights: List<PlannedNight>,
    )

    /**
     * A prepared reconciliation: the three lists in Battles's order.
     * [pantryChecklist] and [bulkChecklist] are list 3 and are populated
     * only for [CheckKind.FULL]; the top-up check asks lists 1 and 2.
     */
    data class Session(
        val kind: CheckKind,
        val sourcePlanIds: List<String>,
        /** List 1: "These are the things you indicated you ran out of." */
        val markedOuts: List<RanOutEvent>,
        /** List 2: "Did you run out of these also?" */
        val possibleOuts: List<PossibleOut>,
        /** List 3, part 1: the pantry staples the app believes she owns. */
        val pantryChecklist: List<PantryItem>,
        /** List 3, part 2: Sam's bulk items with stock on hand. */
        val bulkChecklist: List<PriceItem>,
    ) {
        /** The flow's steps in display order; list 3 exists only in a full check. */
        val stepCount: Int get() = if (kind == CheckKind.FULL) 3 else 2
    }

    /** What Andrea answered inside the flow. Undecided possibles stay undecided. */
    data class SessionChoices(
        val confirmedPossibleKeys: Set<String> = emptySet(),
        val dismissedPossibleKeys: Set<String> = emptySet(),
        /** Pantry checklist entries (pantry item ids) she checked as actually missing. */
        val missingPantryIds: Set<String> = emptySet(),
        /** Sam's bulk item ids she checked as all gone. */
        val missingBulkItemIds: Set<String> = emptySet(),
    )

    /** A completed reconciliation, ready to persist and to plan from. */
    data class Outcome(
        /** Every event row to write: state transitions and new records. */
        val eventsToSave: List<RanOutEvent>,
        /** The reconciled ran-out set: write-back and dedup input. */
        val resolvedOuts: List<ReconciledOut>,
        /** Source plans this check consumed; the app marks them reconciled. */
        val reconciledPlanIds: List<String>,
    )

    // ------------------------------------------------------------------
    // The gate
    // ------------------------------------------------------------------

    /**
     * A reconciliation is pending when there is a finished prior week to
     * reconcile (its [sourcePlans] are the finished, not-yet-reconciled
     * plans) and anything to reconcile in it: ran-out marks still waiting,
     * or an inference that found unconsumed possible-outs. With nothing
     * pending, generation proceeds without the flow; with a skip, nothing
     * here changes, so the same inputs still report pending next time.
     */
    fun hasPendingReconciliation(
        sourcePlans: List<SourcePlan>,
        pendingEvents: List<RanOutEvent>,
        possibleOuts: List<PossibleOut>,
    ): Boolean = sourcePlans.isNotEmpty() && (pendingEvents.isNotEmpty() || possibleOuts.isNotEmpty())

    // ------------------------------------------------------------------
    // Step 1, list 2: the possible-outs inference
    // ------------------------------------------------------------------

    /**
     * Items the source plans' COOKED meals consumed that no later event
     * restocked, staples first, each with its reason. A night counts as
     * cooked when at least one of its cook check-offs is ticked; a night
     * marked not cooking, or never opened in the cook view, consumed
     * nothing and proposes nothing.
     *
     * "Never restocked since" reads the evidence the app actually keeps:
     * - Sam's bulk items: current tracked stock above zero is the item
     *   restocked (or never finished); stock at zero includes stock the
     *   plan itself drew to zero, one of the cases Battles named.
     * - Priced items an owned pantry entry covers by name are asserted on
     *   hand by the pantry itself; list 3 is where that gets corrected.
     * - Pantry staples: only staples the pantry currently records as
     *   owned can "also" have run out; one already marked missing is
     *   known, not inferred.
     * Items already on the ran-out list (pending marks), and items an
     * earlier reconciliation already asked about for these same plans
     * (recorded inference events), are never proposed twice.
     */
    fun inferPossibleOuts(
        sourcePlans: List<SourcePlan>,
        recipesById: Map<String, Recipe>,
        checkoffs: List<CookCheckoff>,
        priceItems: List<PriceItem>,
        pantryItems: List<PantryItem>,
        stockOnHand: Map<String, Quantity>,
        pendingEvents: List<RanOutEvent>,
        recordedEvents: List<RanOutEvent>,
    ): List<PossibleOut> {
        val itemsById = priceItems.associateBy { it.id }
        val ownedNames = pantryItems.filter { it.owned }.map { it.name }
        val ownedStapleKeys = pantryItems.filter { it.owned }
            .map { RanOutKeys.PANTRY_PREFIX + it.name.lowercase() }.toSet()
        val pantryByStapleKey = pantryItems.associateBy {
            RanOutKeys.PANTRY_PREFIX + it.name.lowercase()
        }
        val pendingKeys = pendingEvents.filter { it.state == RanOutState.PENDING }
            .map { it.itemKey }.toSet()
        val sourceIds = sourcePlans.map { it.planId }.toSet()
        val alreadyAskedKeys = recordedEvents
            .filter { it.source == RanOutSource.INFERRED && it.cyclePlanId in sourceIds }
            .map { it.itemKey }.toSet()

        val cookedKeys = checkoffs.filter { it.checked }
            .map { it.planId to it.day }.toSet()

        // First consumption of each item wins the reason line.
        val consumed = linkedMapOf<String, ConsumedItem>()
        for (plan in sourcePlans) {
            for (night in plan.nights) {
                if (night.notCooking) continue
                if ((plan.planId to night.day) !in cookedKeys) continue
                val recipe = recipesById[night.recipeId] ?: continue
                val dayLabel = DAY_LABELS[night.day] ?: night.day
                for (ingredient in recipe.ingredients) {
                    val (key, stapleSide, item) = RanOutKeys.describe(ingredient, itemsById, ownedNames)
                    if (key !in consumed) {
                        consumed[key] = ConsumedItem(
                            key = key,
                            stapleSide = stapleSide,
                            item = item,
                            pantryName = ingredient.pantryItem,
                            usedBy = "$dayLabel's ${recipe.title}",
                        )
                    }
                }
            }
        }

        val possibles = mutableListOf<PossibleOut>()
        for ((key, used) in consumed) {
            if (key in pendingKeys || key in alreadyAskedKeys) continue
            val item = used.item
            when {
                // Pantry staple: ask only while the pantry says owned.
                item == null && used.pantryName != null -> {
                    if (key !in ownedStapleKeys) continue
                    possibles += PossibleOut(
                        itemKey = key,
                        displayName = pantryByStapleKey[key]?.name ?: used.pantryName,
                        stapleSide = true,
                        pantryStapleName = pantryByStapleKey[key]?.name ?: used.pantryName,
                        reason = "Used by ${used.usedBy}; not bought since.",
                    )
                }
                // Sam's bulk: tracked stock is the truth; above zero, it is home.
                item != null && item.homeStore == Store.SAMS_CLUB -> {
                    val onHand = stockOnHand[item.id]?.toBase() ?: 0.0
                    if (onHand > EPSILON) continue
                    possibles += PossibleOut(
                        itemKey = key,
                        displayName = item.name,
                        stapleSide = true,
                        priceItemId = item.id,
                        bulkItemId = item.id,
                        reason = "Used by ${used.usedBy}; the bulk stock is gone.",
                    )
                }
                // Priced item: an owned pantry entry covering it asserts it home.
                item != null -> {
                    if (Costing.pantryNamesCoveringItem(item, ownedNames).isNotEmpty()) continue
                    possibles += PossibleOut(
                        itemKey = key,
                        displayName = item.name,
                        stapleSide = used.stapleSide,
                        priceItemId = item.id,
                        reason = "Used by ${used.usedBy}; not bought since.",
                    )
                }
            }
        }
        // Staples first (Battles's inference order), then by name for stability.
        return possibles.sortedWith(compareBy({ !it.stapleSide }, { it.displayName.lowercase() }))
    }

    private data class ConsumedItem(
        val key: String,
        val stapleSide: Boolean,
        val item: PriceItem?,
        val pantryName: String?,
        /** "<Day>'s <dinner title>", the first cooked use of the item. */
        val usedBy: String,
    )

    // ------------------------------------------------------------------
    // Session assembly and resolution
    // ------------------------------------------------------------------

    /**
     * Assemble the three lists in Battles's order. Lists 1 and 2 come
     * from the caller's event and inference passes; list 3 is the owned
     * pantry plus the Sam's bulk items with stock on hand, full checks
     * only.
     */
    fun buildSession(
        kind: CheckKind,
        sourcePlans: List<SourcePlan>,
        pendingEvents: List<RanOutEvent>,
        possibleOuts: List<PossibleOut>,
        pantryItems: List<PantryItem>,
        priceItems: List<PriceItem>,
        stockOnHand: Map<String, Quantity>,
    ): Session {
        val full = kind == CheckKind.FULL
        return Session(
            kind = kind,
            sourcePlanIds = sourcePlans.map { it.planId },
            // List 1 is her own marks. Pending events with the inferred
            // source are not marks; they are candidates carried forward
            // from a deferred check (Codex review, PR #20), and they
            // belong in list 2 below, not presented as things she said.
            markedOuts = pendingEvents
                .filter { it.state == RanOutState.PENDING && it.source != RanOutSource.INFERRED }
                .sortedWith(compareBy({ it.dateMarked }, { it.displayName.lowercase() })),
            possibleOuts = mergeCarriedPossibles(possibleOuts, pendingEvents),
            pantryChecklist = if (full) {
                pantryItems.filter { it.owned }.sortedBy { it.name.lowercase() }
            } else {
                emptyList()
            },
            bulkChecklist = if (full) {
                priceItems
                    .filter { it.homeStore == Store.SAMS_CLUB }
                    .filter { (stockOnHand[it.id]?.toBase() ?: 0.0) > EPSILON }
                    .sortedBy { it.name.lowercase() }
            } else {
                emptyList()
            },
        )
    }

    /**
     * Deferred inference, carried forward. Choosing "Reconcile later"
     * generates a replacement plan, which deletes the source plan and
     * its cook check-offs; without a carry, the inferred candidates from
     * that plan would vanish instead of waiting for the next attempt
     * (Codex review, PR #20). The app therefore persists an unanswered
     * session's possibles as pending events with the inferred source,
     * and this merge presents them again in list 2 at the next session,
     * behind any fresher inference for the same item.
     */
    private fun mergeCarriedPossibles(
        fresh: List<PossibleOut>,
        pendingEvents: List<RanOutEvent>,
    ): List<PossibleOut> {
        val freshKeys = fresh.map { it.itemKey }.toSet()
        val carried = pendingEvents
            .filter { it.state == RanOutState.PENDING && it.source == RanOutSource.INFERRED }
            .filter { it.itemKey !in freshKeys }
            .map { event ->
                PossibleOut(
                    itemKey = event.itemKey,
                    displayName = event.displayName,
                    stapleSide = event.stapleSide,
                    priceItemId = event.priceItemId,
                    bulkItemId = event.bulkItemId,
                    pantryStapleName = event.pantryStapleName,
                    reason = "Flagged by an earlier check; not bought since.",
                )
            }
        if (carried.isEmpty()) return fresh
        return (fresh + carried)
            .sortedWith(compareBy({ !it.stapleSide }, { it.displayName.lowercase() }))
    }

    /**
     * Resolve a completed session into what gets persisted and what the
     * rest of the flow consumes. Marking, confirming, and checking off
     * all land in one ran-out set; dismissals are recorded so the same
     * item is not asked about again for the same plans, and undecided
     * possibles record nothing.
     *
     * A pantry staple checked missing also resolves the priced items it
     * covers by name: "Potatoes" missing means the russet potatoes are
     * the thing to buy, and as a staple they always land on the list.
     */
    fun resolveSession(
        session: Session,
        choices: SessionChoices,
        priceItems: List<PriceItem>,
        today: String,
    ): Outcome {
        val events = mutableListOf<RanOutEvent>()
        val resolved = linkedMapOf<String, ReconciledOut>()
        val itemsById = priceItems.associateBy { it.id }
        val cyclePlanId = session.sourcePlanIds.firstOrNull()

        fun addResolved(out: ReconciledOut) {
            if (out.itemKey !in resolved) resolved[out.itemKey] = out
        }

        // List 1: her marks, confirmed as a set.
        for (event in session.markedOuts) {
            events += event.copy(state = RanOutState.CONFIRMED)
            addResolved(
                ReconciledOut(
                    itemKey = event.itemKey,
                    displayName = event.displayName,
                    stapleSide = event.stapleSide,
                    priceItemId = event.priceItemId,
                    bulkItemId = event.bulkItemId,
                    pantryStapleName = event.pantryStapleName,
                ),
            )
        }

        // List 2: confirmed join the set; dismissed are recorded, not resolved.
        for (possible in session.possibleOuts) {
            when (possible.itemKey) {
                in choices.confirmedPossibleKeys -> {
                    events += RanOutEvent(
                        itemKey = possible.itemKey,
                        displayName = possible.displayName,
                        stapleSide = possible.stapleSide,
                        priceItemId = possible.priceItemId,
                        bulkItemId = possible.bulkItemId,
                        pantryStapleName = possible.pantryStapleName,
                        dateMarked = today,
                        source = RanOutSource.INFERRED,
                        state = RanOutState.CONFIRMED,
                        cyclePlanId = cyclePlanId,
                    )
                    addResolved(
                        ReconciledOut(
                            itemKey = possible.itemKey,
                            displayName = possible.displayName,
                            stapleSide = possible.stapleSide,
                            priceItemId = possible.priceItemId,
                            bulkItemId = possible.bulkItemId,
                            pantryStapleName = possible.pantryStapleName,
                        ),
                    )
                }
                in choices.dismissedPossibleKeys -> {
                    events += RanOutEvent(
                        itemKey = possible.itemKey,
                        displayName = possible.displayName,
                        stapleSide = possible.stapleSide,
                        priceItemId = possible.priceItemId,
                        bulkItemId = possible.bulkItemId,
                        pantryStapleName = possible.pantryStapleName,
                        dateMarked = today,
                        source = RanOutSource.INFERRED,
                        state = RanOutState.DISMISSED,
                        cyclePlanId = cyclePlanId,
                    )
                }
            }
        }

        // List 3: what she checked off the pantry she owns.
        for (staple in session.pantryChecklist) {
            if (staple.id !in choices.missingPantryIds) continue
            val key = RanOutKeys.PANTRY_PREFIX + staple.name.lowercase()
            events += RanOutEvent(
                itemKey = key,
                displayName = staple.name,
                stapleSide = true,
                pantryStapleName = staple.name,
                dateMarked = today,
                source = RanOutSource.PANTRY_CHECK,
                state = RanOutState.CONFIRMED,
                cyclePlanId = cyclePlanId,
            )
            addResolved(
                ReconciledOut(
                    itemKey = key,
                    displayName = staple.name,
                    stapleSide = true,
                    pantryStapleName = staple.name,
                ),
            )
            // The purchasable form of a missing staple joins the set:
            // priced items this staple's name covers.
            for (coveredId in Costing.pantryCoveredItemIds(setOf(staple.name), priceItems)) {
                val covered = itemsById[coveredId] ?: continue
                addResolved(
                    ReconciledOut(
                        itemKey = RanOutKeys.ITEM_PREFIX + covered.id,
                        displayName = covered.name,
                        stapleSide = true,
                        priceItemId = covered.id,
                        bulkItemId = covered.takeIf { it.homeStore == Store.SAMS_CLUB }?.id,
                    ),
                )
            }
        }
        for (item in session.bulkChecklist) {
            if (item.id !in choices.missingBulkItemIds) continue
            val key = RanOutKeys.ITEM_PREFIX + item.id
            events += RanOutEvent(
                itemKey = key,
                displayName = item.name,
                stapleSide = true,
                priceItemId = item.id,
                bulkItemId = item.id,
                dateMarked = today,
                source = RanOutSource.PANTRY_CHECK,
                state = RanOutState.CONFIRMED,
                cyclePlanId = cyclePlanId,
            )
            addResolved(
                ReconciledOut(
                    itemKey = key,
                    displayName = item.name,
                    stapleSide = true,
                    priceItemId = item.id,
                    bulkItemId = item.id,
                ),
            )
        }

        return Outcome(
            eventsToSave = events,
            resolvedOuts = resolved.values.toList(),
            reconciledPlanIds = session.sourcePlanIds,
        )
    }
}
