package com.battlesbudz.dinnerbot.core.data

import com.battlesbudz.dinnerbot.core.model.ImportedRecipe
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase RI-2: Andrea's own recipes round trip through their table
 * whole — recipe, source link, and arrival time — and a stored origin
 * that is somehow not a user origin reads back as IMPORTED, never as
 * a library recipe (which would hand it to the seed refresh).
 */
class ImportedRecipeMapperTest {

    private val record = ImportedRecipe(
        recipe = Recipe(
            id = "imported-42-lemon-chicken", title = "Lemon Chicken",
            servings = 4, activeMinutes = 35, cuisine = "Mediterranean",
            mainProtein = "Chicken",
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "chicken-breast",
                    quantity = Quantity(1.0, MeasureUnit.POUND),
                    essential = true,
                ),
                RecipeIngredient(
                    pantryItem = "Spring onions",
                    quantity = Quantity(2.0, MeasureUnit.COUNT),
                    note = "sliced", essential = true,
                ),
            ),
            steps = listOf("Sear.", "Serve."),
            origin = RecipeOrigin.IMPORTED,
        ),
        sourceLink = "https://example.com/lemon-chicken",
        createdAtMillis = 123456789L,
    )

    @Test fun roundTripPreservesEverything() {
        val entity = with(SeedMapper) { record.toEntity() }
        assertEquals("IMPORTED", entity.origin)
        val restored = with(SeedMapper) { entity.toModel() }
        assertEquals(record, restored)
    }

    @Test fun manualOriginRoundTrips() {
        val manual = record.copy(
            recipe = record.recipe.copy(
                id = "manual-7-soup", origin = RecipeOrigin.MANUAL,
            ),
            sourceLink = null,
        )
        val restored = with(SeedMapper) { with(SeedMapper) { manual.toEntity() }.toModel() }
        assertEquals(manual, restored)
        assertEquals(RecipeOrigin.MANUAL, restored.recipe.origin)
    }

    @Test fun unknownStoredOriginReadsBackAsImportedNeverLibrary() {
        val entity = with(SeedMapper) { record.toEntity() }
            .copy(origin = "SOMETHING_ELSE")
        val restored = with(SeedMapper) { entity.toModel() }
        assertEquals(RecipeOrigin.IMPORTED, restored.recipe.origin)
    }
}
