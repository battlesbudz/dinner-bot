package com.battlesbudz.dinnerbot.feature.pricebook

import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Store
import kotlin.math.roundToInt

/**
 * Price book editing rules. Andrea's corrections always win over seed data:
 * applying one flags the item userCorrected and recomputes its per-unit price
 * from the pack, so portion costing and the Sam's gate use her number.
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

    /** A copy of [item] carrying Andrea's corrected pack price. */
    fun applyPackPrice(item: PriceItem, newPackPriceCents: Int): PriceItem {
        require(newPackPriceCents > 0) { "price must be positive" }
        return item.copy(
            packPriceCents = newPackPriceCents,
            pricePerUnitCents = newPackPriceCents.toDouble() / item.packQuantity.amount,
            source = "Corrected in app",
            userCorrected = true,
        )
    }

    /** A copy of [item] moved to a different home store. */
    fun applyHomeStore(item: PriceItem, store: Store): PriceItem =
        item.copy(homeStore = store, userCorrected = true)

    /**
     * Record what an item actually cost per unit at a non-home store (the
     * shopping-list correction for a rerouted Sam's item). Replaces any
     * existing alternate for that store.
     */
    fun applyAlternatePrice(item: PriceItem, store: Store, pricePerUnitCents: Double): PriceItem {
        require(pricePerUnitCents > 0.0) { "price must be positive" }
        val updated = item.alternates.filter { it.store != store } +
            com.battlesbudz.dinnerbot.core.model.StorePrice(store, pricePerUnitCents)
        return item.copy(alternates = updated, source = "Corrected in app", userCorrected = true)
    }
}
