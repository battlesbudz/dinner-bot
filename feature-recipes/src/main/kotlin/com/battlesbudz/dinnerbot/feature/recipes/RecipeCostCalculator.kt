package com.battlesbudz.dinnerbot.feature.recipes

import com.battlesbudz.dinnerbot.core.model.Costing
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Recipe
import kotlin.math.roundToInt

/**
 * Estimated cost of a recipe from the price book. Priced ingredients cost
 * quantity times the item's per-unit price; an ingredient with no priced
 * item costs $0. (Before v0.4.3 the pantry staples took that $0 path; they
 * are priced items now and cost their real portion price.) This is an
 * estimate for display, not budget math.
 */
object RecipeCostCalculator {

    fun ingredientCostCents(recipe: Recipe, forServings: Int, itemsById: Map<String, PriceItem>): Int =
        recipe.scaledIngredients(forServings).sumOf { ingredient ->
            val item = ingredient.priceItemId?.let { itemsById[it] }
            val perUnit = item?.pricePerUnitCents
            if (item == null || perUnit == null) {
                0
            } else {
                // Costing.amountInPackUnit, not a raw convertTo: the
                // staples are measured in spoons and cups but sold by
                // weight, and convertTo throws across dimensions.
                val inPackUnit = Costing.amountInPackUnit(ingredient.quantity, item)
                (inPackUnit * perUnit).roundToInt()
            }
        }

    fun costPerServingCents(recipe: Recipe, forServings: Int, itemsById: Map<String, PriceItem>): Int {
        if (forServings <= 0) return 0
        return ingredientCostCents(recipe, forServings, itemsById) / forServings
    }
}
