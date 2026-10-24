package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-1, core-model half: the editor's operations, the override
 * merge on the load path, tonight-only application, and the per-night
 * resolution order (saved recipe, then won't-eat adaptation, then
 * tonight-only edits).
 */
class RecipeOverridesTest {

    private fun item(id: String, name: String, packPrice: Int = 100) = PriceItem(
        id = id, name = name, category = "Test", homeStore = Store.ALDI,
        packDescription = "pack", packQuantity = Quantity(1.0, MeasureUnit.POUND),
        packPriceCents = packPrice, pricePerUnitCents = packPrice.toDouble(),
        checkedDate = "2026-10-23", source = "test",
    )

    private val turkey = item("ground-turkey", "Ground turkey", 198)
    private val pasta = item("spaghetti", "Spaghetti", 209)
    private val onions = item("yellow-onions", "Yellow onions", 150)
    private val peppers = item("bell-peppers", "Bell peppers", 120)
    private val broccoli = item("broccoli", "Broccoli", 130)
    private val book = listOf(turkey, pasta, onions, peppers, broccoli).associateBy { it.id }

    private fun priced(id: String, essential: Boolean, amount: Double = 1.0) = RecipeIngredient(
        priceItemId = id, quantity = Quantity(amount, MeasureUnit.POUND), essential = essential,
    )

    private val dinner = Recipe(
        id = "turkey-pepper-pasta", title = "Turkey Pepper Pasta", servings = 4,
        activeMinutes = 25, cuisine = "Italian", mainProtein = "Ground turkey",
        ingredients = listOf(
            priced("ground-turkey", essential = true),
            priced("spaghetti", essential = true),
            priced("bell-peppers", essential = false),
            priced("broccoli", essential = false),
        ),
        steps = listOf("Cook."),
    )

    // ------------------------------------------------------------------
    // Editor operations
    // ------------------------------------------------------------------

    @Test fun substituteKeepsQuantityNoteAndEssentialMark() {
        val base = listOf(
            RecipeIngredient(
                priceItemId = "bell-peppers", quantity = Quantity(2.0, MeasureUnit.COUNT),
                note = "sliced", essential = false,
            ),
        )
        val result = RecipeEditing.substitute(base, 0, onions)
        val swapped = result.single()
        assertEquals("yellow-onions", swapped.priceItemId)
        assertNull(swapped.pantryItem)
        assertEquals(Quantity(2.0, MeasureUnit.COUNT), swapped.quantity)
        assertEquals("sliced", swapped.note)
        assertFalse(swapped.essential)
    }

    @Test fun substituteTurnsAPantryIngredientIntoAPricedOne() {
        val base = listOf(
            RecipeIngredient(
                pantryItem = "Soy sauce", quantity = Quantity(2.0, MeasureUnit.TABLESPOON),
                essential = false,
            ),
        )
        val soySauce = item("soy-sauce", "Soy sauce", 189)
        val result = RecipeEditing.substitute(base, 0, soySauce)
        assertEquals("soy-sauce", result.single().priceItemId)
        assertNull(result.single().pantryItem)
    }

    @Test fun removeTakesExactlyThatIngredientOut() {
        val result = RecipeEditing.remove(dinner.ingredients, 2)
        assertEquals(
            listOf("ground-turkey", "spaghetti", "broccoli"),
            result.map { it.priceItemId },
        )
    }

    @Test fun setQuantityChangesOnlyThatIngredient() {
        val result = RecipeEditing.setQuantity(
            dinner.ingredients, 0, Quantity(2.5, MeasureUnit.POUND),
        )
        assertEquals(2.5, result[0].quantity.amount, 0.001)
        assertEquals(dinner.ingredients[1], result[1])
        assertEquals(dinner.ingredients.size, result.size)
    }

    @Test fun setEssentialFlipsOnlyThatMark() {
        val result = RecipeEditing.setEssential(dinner.ingredients, 2, true)
        assertTrue(result[2].essential)
        assertEquals(dinner.ingredients[0], result[0])
    }

    @Test fun addAppendsAPricedIngredient() {
        val result = RecipeEditing.add(dinner.ingredients, onions, Quantity(1.0, MeasureUnit.POUND))
        assertEquals(dinner.ingredients.size + 1, result.size)
        assertEquals("yellow-onions", result.last().priceItemId)
        assertTrue("new ingredients default to essential", result.last().essential)
    }

    @Test fun combinedEditsProduceExactlyTheIntendedList() {
        // Her standing version of the dish: peppers swapped for onions,
        // broccoli out, turkey doubled, onions marked essential.
        var list = dinner.ingredients
        list = RecipeEditing.substitute(list, 2, onions)
        list = RecipeEditing.remove(list, 3)
        list = RecipeEditing.setQuantity(list, 0, Quantity(2.0, MeasureUnit.POUND))
        list = RecipeEditing.setEssential(list, 2, true)
        assertEquals(
            listOf(
                RecipeIngredient("ground-turkey", null, Quantity(2.0, MeasureUnit.POUND), null, true),
                RecipeIngredient("spaghetti", null, Quantity(1.0, MeasureUnit.POUND), null, true),
                RecipeIngredient("yellow-onions", null, Quantity(1.0, MeasureUnit.POUND), null, true),
            ),
            list,
        )
    }

    // ------------------------------------------------------------------
    // The override merge
    // ------------------------------------------------------------------

    @Test fun overrideReplacesTheIngredientListWholesaleAndNothingElse() {
        val herList = listOf(priced("ground-turkey", essential = true))
        val merged = EffectiveRecipes.applyOverride(
            dinner, RecipeOverride(dinner.id, herList, updatedAtMillis = 5L),
        )
        assertEquals(herList, merged.ingredients)
        assertEquals(dinner.title, merged.title)
        assertEquals(dinner.steps, merged.steps)
        assertEquals(dinner.servings, merged.servings)
    }

    @Test fun recipeWithoutAnOverrideIsUntouched() {
        val merged = EffectiveRecipes.applyOverrides(listOf(dinner), emptyMap())
        assertEquals(dinner, merged.single())
        val other = RecipeOverride("someone-else", listOf(priced("spaghetti", true)), 1L)
        val merged2 = EffectiveRecipes.applyOverrides(listOf(dinner), mapOf(other.recipeId to other))
        assertEquals(dinner, merged2.single())
        assertFalse(EffectiveRecipes.hasOverride(dinner.id, mapOf(other.recipeId to other)))
    }

    @Test fun overrideForAnUnknownRecipeIsIgnored() {
        val ghost = RecipeOverride("ghost-recipe", listOf(priced("spaghetti", true)), 1L)
        val merged = EffectiveRecipes.applyOverrides(listOf(dinner), mapOf(ghost.recipeId to ghost))
        assertEquals(listOf(dinner), merged)
    }

    // ------------------------------------------------------------------
    // Tonight-only resolution
    // ------------------------------------------------------------------

    private fun night(
        recipeId: String = dinner.id,
        day: String = "MON",
        tonight: TonightOverrides? = null,
        notCooking: Boolean = false,
    ) = PlannedNight(day = day, recipeId = recipeId, notCooking = notCooking, tonightEdits = tonight)

    @Test fun tonightEditsReplaceTheListForThatNightOnly() {
        val tonightList = dinner.ingredients.filter { it.priceItemId != "broccoli" }
        val resolved = EffectiveRecipes.resolveNight(
            night(tonight = TonightOverrides(tonightList)), mapOf(dinner.id to dinner),
        )!!
        assertEquals(tonightList, resolved.ingredients)
        // The saved recipe is untouched: tomorrow's same dish still has it.
        val tomorrow = EffectiveRecipes.resolveNight(night(day = "TUE"), mapOf(dinner.id to dinner))!!
        assertEquals(dinner.ingredients, tomorrow.ingredients)
    }

    @Test fun resolveDinnersSkipsNotCookingNightsAndUnknownRecipes() {
        val nights = listOf(
            night(day = "MON"),
            night(day = "TUE", notCooking = true),
            night(recipeId = "missing", day = "WED"),
        )
        val resolved = EffectiveRecipes.resolveDinners(nights, mapOf(dinner.id to dinner))
        assertEquals(listOf(dinner), resolved)
    }

    @Test fun resolutionOrderIsSavedThenAdaptationThenTonight() {
        // Saved: the full recipe. Adaptation (won't eat peppers, which
        // are removable) strips peppers. Tonight-only: broccoli out too.
        val adapted = RecipeAdaptation.adapt(listOf(dinner), listOf("peppers"), book)
            .recipes.single()
        assertEquals(
            listOf("ground-turkey", "spaghetti", "broccoli"),
            adapted.ingredients.map { it.priceItemId },
        )
        val tonightList = adapted.ingredients.filter { it.priceItemId != "broccoli" }
        val resolved = EffectiveRecipes.resolveNight(
            night(tonight = TonightOverrides(tonightList)), mapOf(dinner.id to adapted),
        )!!
        assertEquals(listOf("ground-turkey", "spaghetti"), resolved.ingredients.map { it.priceItemId })
    }

    @Test fun fromNowOnEditRederivesWontEatBlocking() {
        // Peppers essential + won't-eat blocks the dish; substituting
        // the essential won't-eat ingredient away (a from-now-on edit)
        // makes the household version plannable again.
        val blockedDish = dinner.copy(
            ingredients = listOf(
                priced("ground-turkey", essential = true),
                priced("bell-peppers", essential = true),
            ),
        )
        val before = RecipeAdaptation.adapt(listOf(blockedDish), listOf("peppers"), book)
        assertEquals(setOf(blockedDish.id), before.blockedFromPlanning)

        val herList = RecipeEditing.substitute(blockedDish.ingredients, 1, broccoli)
        val effective = EffectiveRecipes.applyOverride(
            blockedDish, RecipeOverride(blockedDish.id, herList, 9L),
        )
        val after = RecipeAdaptation.adapt(listOf(effective), listOf("peppers"), book)
        assertTrue(after.blockedFromPlanning.isEmpty())
    }
}
