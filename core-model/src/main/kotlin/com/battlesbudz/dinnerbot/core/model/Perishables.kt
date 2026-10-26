package com.battlesbudz.dinnerbot.core.model

/**
 * The two-week plan's freshness split (Phase V2-3). A fortnight is bought
 * as ONE big trip plus a small fresh top-up at the start of week 2, so
 * week 2 food that would spoil in a week at home is not bought a week
 * early. The split rule is category-based, over the price book's existing
 * categories, and lives here so the planner, the grocery list, and the app
 * can never disagree about which list a line belongs on:
 *
 * - Perishable categories: "Produce", "Dairy and eggs", "Meat". Fresh food
 *   with a fridge life measured in days.
 * - Everything else ("Dry and canned", "Frozen", "Bread") keeps for the
 *   fortnight and rides the big trip.
 *
 * One exception, by item rather than category (Phase C1): a bulk-pack
 * item — one carrying freeze guidance ([PriceItem.freezeNote]) — is a
 * stock purchase. It is bought on the big trip and its leftovers are
 * stored at home, which is exactly what the freeze guidance describes,
 * at whatever store the user buys it.
 */
object Perishables {

    /** Price book categories whose food spoils too fast to buy a week ahead. */
    val PERISHABLE_CATEGORIES: Set<String> = setOf("Produce", "Dairy and eggs", "Meat")

    fun isPerishable(item: PriceItem): Boolean = item.category in PERISHABLE_CATEGORIES

    /**
     * True when a week 2 [line] belongs on the fresh top-up list: its item
     * is perishable and the item is not a bulk-pack (freeze-note) item.
     * Week 1 lines never split; they are all bought on the big trip.
     */
    fun isTopUpLine(line: GroceryLine, item: PriceItem?): Boolean =
        item != null && item.freezeNote == null && isPerishable(item)
}
