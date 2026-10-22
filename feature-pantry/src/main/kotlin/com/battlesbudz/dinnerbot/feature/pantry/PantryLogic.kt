package com.battlesbudz.dinnerbot.feature.pantry

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store

/** Pure pantry and bulk-stock rules. */
object PantryLogic {

    fun slug(name: String): String =
        name.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun newStaple(name: String): PantryItem = PantryItem(id = slug(name), name = name.trim(), owned = true)

    fun toggle(items: List<PantryItem>, id: String): List<PantryItem> =
        items.map { if (it.id == id) it.copy(owned = !it.owned) else it }

    fun ownedNames(items: List<PantryItem>): Set<String> =
        items.filter { it.owned }.map { it.name }.toSet()

    /** Price items that live at Sam's Club and are therefore bulk-stock tracked. */
    fun bulkItems(priceItems: List<PriceItem>): List<PriceItem> =
        priceItems.filter { it.homeStore == Store.SAMS_CLUB }

    /** Sensible stepper increment for an item's stock, in the item's pack unit. */
    fun stepFor(item: PriceItem): Double = when (item.packQuantity.unit.dimension) {
        com.battlesbudz.dinnerbot.core.model.Dimension.WEIGHT -> 0.25
        com.battlesbudz.dinnerbot.core.model.Dimension.VOLUME -> 1.0
        com.battlesbudz.dinnerbot.core.model.Dimension.COUNT -> 1.0
    }

    /** Adjust stock by [deltaSteps] steps, clamped at zero, in the item's pack unit. */
    fun adjustStock(item: PriceItem, current: Quantity?, deltaSteps: Int): BulkStock {
        val unit: MeasureUnit = item.packQuantity.unit
        val currentAmount = current?.convertTo(unit)?.amount ?: 0.0
        val next = (currentAmount + stepFor(item) * deltaSteps).coerceAtLeast(0.0)
        return BulkStock(itemId = item.id, onHand = Quantity(next, unit))
    }

    fun stockMap(stocks: List<BulkStock>): Map<String, Quantity> =
        stocks.associate { it.itemId to it.onHand }
}
