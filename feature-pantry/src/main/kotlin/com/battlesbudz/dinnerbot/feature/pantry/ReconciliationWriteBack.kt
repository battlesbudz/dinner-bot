package com.battlesbudz.dinnerbot.feature.pantry

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.ReconciledOut
import com.battlesbudz.dinnerbot.core.model.Store

/**
 * The Sunday reconciliation write-back (Phase V2-7, scope B4): a
 * completed reconciliation's ran-out set corrects the pantry owned state
 * and the Sam's bulk stock BEFORE the new week is planned, so the planner
 * never assumes an ingredient is home when Sunday just established it is
 * not. The next Sunday starts from this corrected truth.
 *
 * For each resolved ran-out item:
 * - a pantry staple becomes not owned, until it is bought again and
 *   marked owned the way buying is recorded today (the Pantry tab);
 * - a Sam's bulk item's tracked stock is gone: its stock row is removed;
 * - a priced item an owned pantry entry covers by name flips exactly
 *   those entries to not owned, so the item stops reading as already
 *   home and lands on the list whenever a dinner needs it.
 *
 * Pure: the caller persists the returned lists.
 */
object ReconciliationWriteBack {

    data class Result(
        val pantryItems: List<PantryItem>,
        /** Bulk stock rows still standing; a resolved-out item's row is absent. */
        val bulkStocks: List<BulkStock>,
    )

    fun apply(
        pantryItems: List<PantryItem>,
        bulkStocks: List<BulkStock>,
        resolvedOuts: List<ReconciledOut>,
        priceItems: List<PriceItem>,
    ): Result {
        if (resolvedOuts.isEmpty()) return Result(pantryItems, bulkStocks)
        val itemsById = priceItems.associateBy { it.id }
        val flipNames = mutableSetOf<String>()
        val zeroStockIds = mutableSetOf<String>()

        for (out in resolvedOuts) {
            out.pantryStapleName?.let { flipNames += it.lowercase() }
            out.bulkItemId?.let { zeroStockIds += it }
            val item = out.priceItemId?.let { itemsById[it] } ?: continue
            if (item.homeStore == Store.SAMS_CLUB) zeroStockIds += item.id
            // The entries that made this item read as already home flip.
            val ownedNames = pantryItems.filter { it.owned }.map { it.name }
            Costing.pantryNamesCoveringItem(item, ownedNames).forEach { flipNames += it.lowercase() }
        }

        return Result(
            pantryItems = pantryItems.map { pantryItem ->
                if (pantryItem.owned && pantryItem.name.lowercase() in flipNames) {
                    pantryItem.copy(owned = false)
                } else {
                    pantryItem
                }
            },
            bulkStocks = bulkStocks.filter { it.itemId !in zeroStockIds },
        )
    }
}
