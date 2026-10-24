package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-2: Andrea's own recipes (imported and hand-made) are exempt
 * from the won't-eat planner (Battles's ruling 3), and the load path
 * merges them into the one effective recipe set.
 */
class ImportedRecipesTest {

    private fun item(id: String, name: String) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = Store.ALDI, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-24", source = "test",
    )

    private val items = listOf(item("bell-peppers", "Green bell peppers"))
    private val itemsById = items.associateBy { it.id }

    private fun recipe(id: String, origin: RecipeOrigin, essential: Boolean) = Recipe(
        id = id, title = id, servings = 4, activeMinutes = 10,
        cuisine = "Test", mainProtein = "Chicken",
        ingredients = listOf(
            RecipeIngredient(
                priceItemId = "bell-peppers",
                quantity = Quantity(1.0, MeasureUnit.POUND),
                essential = essential,
            ),
        ),
        steps = listOf("Cook."),
        origin = origin,
    )

    @Test fun importedRecipeWithEssentialWontEatIsPlannableAndUnstripped() {
        val imported = recipe("imported-1", RecipeOrigin.IMPORTED, essential = true)
        val result = RecipeAdaptation.adapt(listOf(imported), listOf("bell peppers"), itemsById)
        assertTrue(result.blockedFromPlanning.isEmpty())
        assertEquals(listOf(imported), result.plannable)
        // Cooks as written: nothing stripped at read time either.
        assertEquals(imported.ingredients, result.recipes.single().ingredients)
    }

    @Test fun manualRecipeWithEssentialWontEatIsPlannableAndUnstripped() {
        val manual = recipe("manual-1", RecipeOrigin.MANUAL, essential = true)
        val result = RecipeAdaptation.adapt(listOf(manual), listOf("bell peppers"), itemsById)
        assertTrue(result.blockedFromPlanning.isEmpty())
        assertEquals(manual.ingredients, result.recipes.single().ingredients)
    }

    @Test fun importedRecipeWithRemovableWontEatIsNotStripped() {
        val imported = recipe("imported-2", RecipeOrigin.IMPORTED, essential = false)
        val result = RecipeAdaptation.adapt(listOf(imported), listOf("bell peppers"), itemsById)
        assertEquals(imported.ingredients, result.recipes.single().ingredients)
    }

    @Test fun libraryRecipeRulesAreUnchangedByTheExemption() {
        val blocked = recipe("lib-1", RecipeOrigin.LIBRARY, essential = true)
        val stripped = recipe("lib-2", RecipeOrigin.LIBRARY, essential = false)
        val result = RecipeAdaptation.adapt(
            listOf(blocked, stripped), listOf("bell peppers"), itemsById,
        )
        assertEquals(setOf("lib-1"), result.blockedFromPlanning)
        assertTrue(result.recipes.first { it.id == "lib-2" }.ingredients.isEmpty())
    }

    @Test fun mergeUserRecipesAppendsUserRecipesAfterTheLibrary() {
        val library = listOf(recipe("lib-1", RecipeOrigin.LIBRARY, essential = true))
        val user = recipe("imported-1", RecipeOrigin.IMPORTED, essential = true)
        assertEquals(
            listOf("lib-1", "imported-1"),
            EffectiveRecipes.mergeUserRecipes(library, listOf(user)).map { it.id },
        )
    }

    @Test fun mergeUserRecipesKeepsTheLibraryCopyOnAnIdCollision() {
        val library = listOf(recipe("same", RecipeOrigin.LIBRARY, essential = true))
        val user = recipe("same", RecipeOrigin.IMPORTED, essential = true)
        val merged = EffectiveRecipes.mergeUserRecipes(library, listOf(user))
        assertEquals(1, merged.size)
        assertEquals(RecipeOrigin.LIBRARY, merged.single().origin)
    }

    @Test fun importedIdsCarryTheirNamespaceAndStayDistinct() {
        val a = ImportedRecipeIds.newId(RecipeOrigin.IMPORTED, "My Dinner!", 100L)
        val b = ImportedRecipeIds.newId(RecipeOrigin.MANUAL, "My Dinner!", 100L)
        assertTrue(a.startsWith("imported-100-"))
        assertTrue(b.startsWith("manual-100-"))
        assertFalse(a == b)
        assertTrue(a.endsWith("my-dinner"))
    }

    @Test fun importedRecipeRecordRejectsALibraryRecipe() {
        val library = recipe("lib-1", RecipeOrigin.LIBRARY, essential = true)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            ImportedRecipe(recipe = library, sourceLink = null, createdAtMillis = 1L)
        }
    }
}
