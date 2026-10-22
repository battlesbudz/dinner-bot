package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeAdaptationTest {

    private fun item(id: String, name: String, packPrice: Int = 100) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
        checkedDate = "2026-10-22", source = "test",
    )

    private val turkey = item("ground-turkey", "Ground turkey", 198)
    private val pasta = item("spaghetti", "Spaghetti", 209)
    private val onions = item("yellow-onions", "Yellow onions", 150)
    private val eggsItem = item("eggs", "Eggs", 185)
    private val book = listOf(turkey, pasta, onions, eggsItem).associateBy { it.id }

    private fun priced(id: String, essential: Boolean) = RecipeIngredient(
        priceItemId = id, quantity = Quantity(1.0, MeasureUnit.POUND), essential = essential,
    )

    private val spaghettiNight = Recipe(
        id = "spaghetti-night", title = "Spaghetti Night", servings = 4, activeMinutes = 20,
        cuisine = "Italian", mainProtein = "Ground turkey",
        ingredients = listOf(
            priced("ground-turkey", essential = true),
            priced("spaghetti", essential = true),
            priced("yellow-onions", essential = false),
        ),
        steps = listOf("Cook."),
    )

    private val eggFriedRice = Recipe(
        id = "egg-fried-rice", title = "Egg Fried Rice", servings = 4, activeMinutes = 15,
        cuisine = "Asian", mainProtein = "Egg",
        ingredients = listOf(
            priced("eggs", essential = true),
            priced("yellow-onions", essential = false),
            RecipeIngredient(
                pantryItem = "Soy sauce",
                quantity = Quantity(1.0, MeasureUnit.TABLESPOON),
                essential = false,
            ),
        ),
        steps = listOf("Fry."),
    )

    @Test fun removableWontEatIngredientComesOutAndRecipeStays() {
        val result = RecipeAdaptation.adapt(listOf(spaghettiNight), listOf("onion"), book)
        val adapted = result.recipes.single()
        assertEquals(listOf("ground-turkey", "spaghetti"), adapted.ingredients.map { it.priceItemId })
        assertTrue(result.blockedFromPlanning.isEmpty())
        assertEquals(listOf("spaghetti-night"), result.plannable.map { it.id })
    }

    @Test fun essentialWontEatIngredientBlocksRecipeFromPlanningOnly() {
        val result = RecipeAdaptation.adapt(listOf(eggFriedRice), listOf("egg"), book)
        assertEquals(setOf("egg-fried-rice"), result.blockedFromPlanning)
        assertTrue(result.plannable.isEmpty())
        // Browsing is unchanged: the stored recipe keeps its essential ingredient.
        assertTrue(result.recipes.single().ingredients.any { it.priceItemId == "eggs" })
    }

    @Test fun matchingIgnoresCaseAndPluralPhrasing() {
        val upper = RecipeAdaptation.adapt(listOf(spaghettiNight), listOf("ONION"), book)
        assertEquals(2, upper.recipes.single().ingredients.size)
        val plural = RecipeAdaptation.adapt(listOf(spaghettiNight), listOf("Onions"), book)
        assertEquals(2, plural.recipes.single().ingredients.size)
        val exact = RecipeAdaptation.adapt(listOf(spaghettiNight), listOf("yellow onions"), book)
        assertEquals(2, exact.recipes.single().ingredients.size)
    }

    @Test fun removablePantryIngredientComesOutToo() {
        val result = RecipeAdaptation.adapt(listOf(eggFriedRice), listOf("soy sauce"), book)
        val adapted = result.recipes.single()
        assertTrue(adapted.ingredients.none { it.pantryItem == "Soy sauce" })
        assertTrue(result.blockedFromPlanning.isEmpty())
    }

    @Test fun unrelatedWontEatChangesNothing() {
        val result = RecipeAdaptation.adapt(
            listOf(spaghettiNight, eggFriedRice), listOf("mushroom"), book,
        )
        assertEquals(listOf(spaghettiNight, eggFriedRice), result.recipes)
        assertTrue(result.blockedFromPlanning.isEmpty())
    }

    @Test fun emptyWontEatReturnsRecipesUnchanged() {
        val result = RecipeAdaptation.adapt(listOf(spaghettiNight), emptyList(), book)
        assertEquals(listOf(spaghettiNight), result.recipes)
        assertTrue(result.blockedFromPlanning.isEmpty())
    }

    @Test fun removedIngredientDropsOutOfCostingAndGroceryLines() {
        val adapted = RecipeAdaptation.adapt(listOf(spaghettiNight), listOf("onion"), book)
        val costed = Costing.costRecipes(adapted.plannable, 4, book, emptyMap())
        assertTrue(costed.lines.none { it.itemId == "yellow-onions" })
        val unadapted = Costing.costRecipes(listOf(spaghettiNight), 4, book, emptyMap())
        assertTrue(unadapted.lines.any { it.itemId == "yellow-onions" })
        assertTrue(costed.cashNeededCents < unadapted.cashNeededCents)
    }

    @Test fun blockedRecipeContributesNothingToCosting() {
        val adapted = RecipeAdaptation.adapt(listOf(eggFriedRice), listOf("eggs"), book)
        assertFalse(adapted.plannable.any { it.id == "egg-fried-rice" })
        val costed = Costing.costRecipes(adapted.plannable, 4, book, emptyMap())
        assertEquals(0, costed.cashNeededCents)
    }
}
