package com.battlesbudz.dinnerbot.feature.pricebook

import com.battlesbudz.dinnerbot.core.model.PriceItem
import kotlin.math.roundToInt

/**
 * Price book editing rules. The user's corrections always win over seed
 * data: applying one flags the item userCorrected and recomputes its
 * per-unit price from the pack, so portion costing uses their number.
 */
object PriceCorrection {

    /** Parse a typed dollar amount ("2", "2.5", "2.49") into cents, or null if it is not a price. */
    fun parseToCents(text: String): Int? {
        val cleaned = text.trim().removePrefix("$")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (value < 0.0) return null
        return (value * 100).roundToInt()
    }

    /**
     * A copy of [item] carrying the user's corrected pack price. [source]
     * records which door the correction came through: a typed correction
     * by default, "Corrected by barcode scan" for Phase V2-5 scans. The
     * write itself is identical either way: their number wins, the
     * per-unit price is recomputed, and the item is flagged
     * user-corrected.
     */
    fun applyPackPrice(item: PriceItem, newPackPriceCents: Int, source: String = "Corrected in app"): PriceItem {
        require(newPackPriceCents > 0) { "price must be positive" }
        return item.copy(
            packPriceCents = newPackPriceCents,
            pricePerUnitCents = newPackPriceCents.toDouble() / item.packQuantity.amount,
            source = source,
            userCorrected = true,
            // A real price replaces an import guess for good
            // (user correction, 2026-10-24).
            guessPriced = false,
        )
    }

    /** A copy of [item] moved to a different home store. */
    fun applyHomeStore(item: PriceItem, store: String): PriceItem =
        item.copy(homeStore = store, userCorrected = true)

    /**
     * Record what an item actually cost per unit at a non-home store (the
     * shopping-list correction for a line bought at another of the
     * user's stores). Replaces any existing alternate for that store.
     * [source] records which door the correction came through, exactly
     * as on [applyPackPrice].
     */
    fun applyAlternatePrice(
        item: PriceItem,
        store: String,
        pricePerUnitCents: Double,
        source: String = "Corrected in app",
    ): PriceItem {
        require(pricePerUnitCents > 0.0) { "price must be positive" }
        val updated = item.alternates.filter { it.store != store } +
            com.battlesbudz.dinnerbot.core.model.StorePrice(store, pricePerUnitCents)
        return item.copy(alternates = updated, source = source, userCorrected = true)
    }
}
