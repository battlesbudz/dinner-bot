package com.battlesbudz.dinnerbot.core.model

import kotlinx.serialization.Serializable

/**
 * Sunday reconciliation types (Phase V2-7, Battles's design in scope B4).
 *
 * During the week, the cook view records which ingredients a planned dinner
 * used ([CookCheckoff]) and which ones Andrea used all of and ran out of
 * ([RanOutEvent] with source [RanOutSource.COOKING]). Before a new week is
 * planned, the Sunday flow shows those marks, asks about the items the last
 * plan's cooked dinners consumed ([PossibleOut], source
 * [RanOutSource.INFERRED]), and walks the pantry she owns (source
 * [RanOutSource.PANTRY_CHECK]). What survives that flow is the reconciled
 * ran-out set ([ReconciledOut]): it is written back to pantry and stock
 * state before planning, and merged into the new week's shopping list by
 * the dedup rules (a ran-out staple is always listed; a ran-out ingredient
 * only when a planned dinner uses it; never twice).
 */

/** Where a ran-out record came from. */
@Serializable
enum class RanOutSource { COOKING, INFERRED, PANTRY_CHECK }

/**
 * Lifecycle of a ran-out record: PENDING (marked while cooking, waiting
 * for Sunday), CONFIRMED (part of a completed reconciliation's ran-out
 * set), DISMISSED (the inference asked and Andrea said she still has it),
 * RESTOCKED (bought again on a reconciled week's shop, or marked owned
 * again in the pantry).
 */
@Serializable
enum class RanOutState { PENDING, CONFIRMED, DISMISSED, RESTOCKED }

/**
 * One dated ran-out record for one item. The item is named three ways so
 * every surface can resolve it: [itemKey] is the stable identity the cook
 * check-offs use ("item:<price item id>" or "pantry:<lowercased name>"),
 * [priceItemId] / [bulkItemId] point at the price book and the Sam's bulk
 * stock when the item is priced, and [pantryStapleName] names the pantry
 * checklist staple when the item is one.
 */
@Serializable
data class RanOutEvent(
    /** Room row id; 0 until persisted. */
    val id: Long = 0,
    val itemKey: String,
    val displayName: String,
    /** True when the dedup rule treats this item as a staple (always listed). */
    val stapleSide: Boolean,
    val priceItemId: String? = null,
    val bulkItemId: String? = null,
    val pantryStapleName: String? = null,
    /** ISO date (yyyy-MM-dd) the ran-out was marked or confirmed. */
    val dateMarked: String,
    val source: RanOutSource,
    val state: RanOutState = RanOutState.PENDING,
    /**
     * Id of the plan whose cooking cycle this record belongs to, when
     * known: inference records carry the plan they were inferred from, so
     * the same item is never asked about twice for the same week.
     */
    val cyclePlanId: String? = null,
)

/** One ingredient check-off inside one planned dinner (Phase V2-7, step 0). */
@Serializable
data class CookCheckoff(
    val planId: String,
    /** Night code of the planned dinner, MON..SUN. */
    val day: String,
    val itemKey: String,
    val checked: Boolean,
)

/**
 * An item the Sunday inference believes may also be out, with the
 * one-line reason it thinks so. It only proposes; the flow asks.
 */
data class PossibleOut(
    val itemKey: String,
    val displayName: String,
    val stapleSide: Boolean,
    val priceItemId: String? = null,
    val bulkItemId: String? = null,
    val pantryStapleName: String? = null,
    val reason: String,
)

/** One item of a completed reconciliation's ran-out set: the dedup input. */
data class ReconciledOut(
    val itemKey: String,
    val displayName: String,
    val stapleSide: Boolean,
    val priceItemId: String? = null,
    val bulkItemId: String? = null,
    val pantryStapleName: String? = null,
)

/**
 * The item keys the cook view, check-offs, and ran-out records share, and
 * the staple-side rule: an item is a staple for the dedup rules when it is
 * flagged [PriceItem.staple] in the price book (the pantry staples, priced
 * since v0.4.3), a legacy unpriced pantry staple, a Sam's Club bulk item
 * (the household's standing stock), or a priced item an owned pantry entry
 * covers by name (the line the app already draws between staples and
 * recipe-only items).
 */
object RanOutKeys {
    const val ITEM_PREFIX = "item:"
    const val PANTRY_PREFIX = "pantry:"

    fun forIngredient(ingredient: RecipeIngredient): String =
        ingredient.pantryItem?.let { PANTRY_PREFIX + it.lowercase() }
            ?: ITEM_PREFIX + Costing.substituteItemId(ingredient.priceItemId!!)

    fun isPantryKey(key: String): Boolean = key.startsWith(PANTRY_PREFIX)

    fun priceItemIdOf(key: String): String? =
        if (key.startsWith(ITEM_PREFIX)) key.removePrefix(ITEM_PREFIX) else null

    fun pantryNameOf(key: String): String? =
        if (key.startsWith(PANTRY_PREFIX)) key.removePrefix(PANTRY_PREFIX) else null

    fun stapleSideFor(item: PriceItem, pantryOwnedNames: Collection<String>): Boolean =
        item.staple ||
            item.homeStore == Store.SAMS_CLUB ||
            Costing.pantryNamesCoveringItem(item, pantryOwnedNames).isNotEmpty()

    /** A priced ingredient's staple side and ids, resolved against the book. */
    fun describe(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
        pantryOwnedNames: Collection<String>,
    ): Triple<String, Boolean, PriceItem?> {
        val key = forIngredient(ingredient)
        if (ingredient.pantryItem != null) return Triple(key, true, null)
        val item = itemsById[Costing.substituteItemId(ingredient.priceItemId!!)]
            ?: itemsById[ingredient.priceItemId]
        return Triple(key, item != null && stapleSideFor(item, pantryOwnedNames), item)
    }

    /** The ran-out record the cook view's "ran out" mark writes for one ingredient. */
    fun cookingEvent(
        ingredient: RecipeIngredient,
        itemsById: Map<String, PriceItem>,
        pantryOwnedNames: Collection<String>,
        today: String,
        cyclePlanId: String?,
    ): RanOutEvent {
        val (key, stapleSide, item) = describe(ingredient, itemsById, pantryOwnedNames)
        return RanOutEvent(
            itemKey = key,
            displayName = item?.name ?: ingredient.pantryItem ?: key,
            stapleSide = stapleSide,
            priceItemId = item?.id,
            bulkItemId = item?.takeIf { it.homeStore == Store.SAMS_CLUB }?.id,
            pantryStapleName = ingredient.pantryItem,
            dateMarked = today,
            source = RanOutSource.COOKING,
            state = RanOutState.PENDING,
            cyclePlanId = cyclePlanId,
        )
    }
}
