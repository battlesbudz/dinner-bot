package com.battlesbudz.dinnerbot.feature.recipes

import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertEquals
import org.junit.Test

class RecipeCostCalculatorTest {
    private val turkey = PriceItem(
        id = "ground-turkey", name = "Ground turkey", category = "Meat",
        homeStore = Store.WALMART, packDescription = "per lb",
        packQuantity = Quantity.pounds(1.0), packPriceCents = 198,
        pricePerUnitCents = 198.0, checkedDate = "2026-10-22", source = "test",
    )

    private val recipe = Recipe(
        id = "r", title = "T", servings = 4, activeMinutes = 10, cuisine = "American",
        mainProtein = "Ground turkey",
        ingredients = listOf(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity.pounds(1.0)),
            RecipeIngredient(pantryItem = "Salt", quantity = Quantity(1.0, com.battlesbudz.dinnerbot.core.model.MeasureUnit.TEASPOON)),
        ),
        steps = listOf("Cook."),
    )

    @Test fun pricedIngredientsCostAndPantryIsFree() {
        val total = RecipeCostCalculator.ingredientCostCents(recipe, 4, mapOf("ground-turkey" to turkey))
        assertEquals(198, total)
        assertEquals(49, RecipeCostCalculator.costPerServingCents(recipe, 4, mapOf("ground-turkey" to turkey)))
    }

    @Test fun scalingDoublesCost() {
        val total = RecipeCostCalculator.ingredientCostCents(recipe, 8, mapOf("ground-turkey" to turkey))
        assertEquals(396, total)
    }

    @Test fun unknownItemCostsZero() {
        val total = RecipeCostCalculator.ingredientCostCents(recipe, 4, emptyMap())
        assertEquals(0, total)
    }
}
