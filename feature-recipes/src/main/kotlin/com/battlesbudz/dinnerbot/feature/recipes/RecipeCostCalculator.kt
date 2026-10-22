package com.battlesbudz.dinnerbot.feature.recipes

import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import kotlin.math.roundToInt

/**
 * Estimated cost of a recipe from the price book. Priced ingredients cost
 * quantity times the item's per-unit price; pantry staples cost $0 because
 * they are already home. This is an estimate for display, not budget math.
 */
object RecipeCostCalculator {

    fun ingredientCostCents(recipe: Recipe, forServings: Int, itemsById: Map<String, PriceItem>): Int =
        recipe.scaledIngredients(forServings).sumOf { ingredient ->
            val item = ingredient.priceItemId?.let { itemsById[it] }
            val perUnit = item?.pricePerUnitCents
            if (item == null || perUnit == null) {
                0
            } else {
                val inPackUnit = ingredient.quantity.convertTo(item.packQuantity.unit).amount
                (inPackUnit * perUnit).roundToInt()
            }
        }

    fun costPerServingCents(recipe: Recipe, forServings: Int, itemsById: Map<String, PriceItem>): Int {
        if (forServings <= 0) return 0
        return ingredientCostCents(recipe, forServings, itemsById) / forServings
    }
}
