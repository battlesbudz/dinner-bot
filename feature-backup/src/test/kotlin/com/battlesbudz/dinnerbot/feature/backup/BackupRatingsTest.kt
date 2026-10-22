package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import com.battlesbudz.dinnerbot.core.model.Store
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-1: ratings ride in backup format v2; v1 files still import. */
class BackupRatingsTest {

    private fun data(ratings: List<BackupRecipeRating>) = BackupData(
        settings = HouseholdSettings(
            householdSize = 5,
            cookNights = listOf("MON", "WED", "FRI"),
            weeklyBudgetCents = 9000,
            onboardingComplete = true,
        ),
        priceItems = listOf(
            PriceItem(
                id = "eggs", name = "Eggs", category = "Dairy",
                homeStore = Store.ALDI, packDescription = "12 ct",
                packQuantity = Quantity(12.0, MeasureUnit.COUNT), packPriceCents = 185,
                pricePerUnitCents = 15.4, checkedDate = "2026-10-22", source = "test",
            ),
        ),
        pantryItems = listOf(PantryItem("salt", "Salt", owned = true)),
        bulkStock = listOf(BulkStock("pork-tenderloin", Quantity.pounds(4.5))),
        recipeRatings = ratings,
    )

    @Test fun ratingsRoundTripThroughExportImport() {
        val original = data(
            listOf(
                BackupRecipeRating("turkey-tacos", RecipeRating.UP),
                BackupRecipeRating("bean-soup", RecipeRating.DOWN),
            ),
        )
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(BackupData.CURRENT_VERSION, restored.version)
        assertEquals(original.recipeRatings, restored.recipeRatings)
        assertEquals(original.priceItems, restored.priceItems)
        assertEquals(original.settings, restored.settings)
    }

    @Test fun versionOneBackupImportsWithNoRatings() {
        // A real v1 file predates the recipeRatings field entirely.
        val exported = BackupEngine.export(data(listOf(BackupRecipeRating("x", RecipeRating.UP))))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v1 = JsonObject(parsed - "recipeRatings" + ("version" to kotlinx.serialization.json.JsonPrimitive(1)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v1))
        assertEquals(1, restored.version)
        assertTrue(restored.recipeRatings.isEmpty())
        assertEquals(1, restored.priceItems.size)
    }

    @Test fun duplicateRatingsAreRejected() {
        val dup = data(
            listOf(
                BackupRecipeRating("same-recipe", RecipeRating.UP),
                BackupRecipeRating("same-recipe", RecipeRating.DOWN),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(dup))
        }
    }
}
