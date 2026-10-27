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
        homeStore = "walmart", packDescription = "per lb",
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

    @Test fun stapleMeasuredByVolumeAgainstAWeightPackCostsInsteadOfCrashing() {
        // v0.4.3 (Codex P1, PR #23): rice is measured in cups and sold by
        // the pound. The calculator must not throw across the dimension
        // gap; it prices the portion on Costing's base-amount convention:
        // 2 cups = 16 base units, the pack unit's base factor is 16, so
        // the portion is 1.0 pack units at 98.3 cents.
        val rice = PriceItem(
            id = "long-grain-white-rice", name = "Long grain white rice",
            category = "Dry and canned", homeStore = "aldi",
            packDescription = "3 lb bag",
            packQuantity = Quantity.pounds(3.0), packPriceCents = 295,
            pricePerUnitCents = 98.3, checkedDate = "2026-10-23", source = "test",
            staple = true,
        )
        val riceRecipe = Recipe(
            id = "rice-r", title = "Rice", servings = 4, activeMinutes = 10,
            cuisine = "Test", mainProtein = "Egg",
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "long-grain-white-rice",
                    quantity = Quantity(2.0, com.battlesbudz.dinnerbot.core.model.MeasureUnit.CUP),
                ),
            ),
            steps = listOf("Cook."),
        )
        val total = RecipeCostCalculator.ingredientCostCents(
            riceRecipe, 4, mapOf("long-grain-white-rice" to rice),
        )
        assertEquals(98, total)
    }
}
