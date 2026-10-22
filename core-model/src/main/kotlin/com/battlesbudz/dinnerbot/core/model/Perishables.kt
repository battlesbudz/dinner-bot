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
 * One exception, by store rather than category: a line actually bought at
 * Sam's Club is a bulk stock purchase. It is bought on the big trip (the
 * Sam's run IS the big trip) and its leftovers are stored at home, which
 * is exactly what the freeze guidance on bulk packs describes. A Sam's
 * item the savings gate reroutes to a regular store is bought fresh like
 * anything else there, so a rerouted perishable line does split to the
 * top-up.
 */
object Perishables {

    /** Price book categories whose food spoils too fast to buy a week ahead. */
    val PERISHABLE_CATEGORIES: Set<String> = setOf("Produce", "Dairy and eggs", "Meat")

    fun isPerishable(item: PriceItem): Boolean = item.category in PERISHABLE_CATEGORIES

    /**
     * True when a week 2 [line] belongs on the fresh top-up list: its item
     * is perishable and the line is not a Sam's Club stock purchase.
     * Week 1 lines never split; they are all bought on the big trip.
     */
    fun isTopUpLine(line: GroceryLine, item: PriceItem?): Boolean =
        line.store != Store.SAMS_CLUB && item != null && isPerishable(item)
}
