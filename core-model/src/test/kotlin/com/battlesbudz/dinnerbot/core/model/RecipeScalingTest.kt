package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeScalingTest {
    private val recipe = Recipe(
        id = "test", title = "Test", servings = 4, activeMinutes = 10,
        cuisine = "American", mainProtein = "Ground turkey",
        ingredients = listOf(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity.pounds(1.0)),
            RecipeIngredient(pantryItem = "Salt", quantity = Quantity(1.0, MeasureUnit.TEASPOON)),
        ),
        steps = listOf("Cook it."),
    )

    @Test fun doublingServingsDoublesQuantities() {
        val scaled = recipe.scaledIngredients(8)
        assertEquals(2.0, scaled[0].quantity.amount, 0.001)
    }

    @Test fun sameServingsReturnsSameQuantities() {
        val scaled = recipe.scaledIngredients(4)
        assertEquals(1.0, scaled[0].quantity.amount, 0.001)
    }

    @Test fun ingredientMustBeItemOrPantry() {
        val bad = runCatching {
            RecipeIngredient(quantity = Quantity.count(1.0))
        }
        assertTrue(bad.isFailure)
    }
}
