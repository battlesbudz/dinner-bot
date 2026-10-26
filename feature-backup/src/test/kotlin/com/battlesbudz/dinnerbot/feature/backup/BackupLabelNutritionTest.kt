package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Format v9 (v0.6.3): scanned label facts ride in the backup, and a
 * household override carries her edited steps. A phone swap must not
 * lose either. Files from format v8 import with no label facts and
 * every override's steps unset, exactly as those phones behaved.
 */
class BackupLabelNutritionTest {

    private fun item(id: String, name: String) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = Store.ALDI, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-23", source = "test",
    )

    private val book = listOf(item("spaghetti", "Spaghetti"), item("eggs", "Eggs"))

    private val spaghettiFacts = BackupLabelNutrition(
        itemId = "spaghetti",
        barcode = "3017620422003",
        per100g = NutritionValues(
            caloriesKcal = 350.0, proteinG = 12.0, fatG = 2.0, sodiumMg = 5.0,
        ),
        perServing = NutritionValues(caloriesKcal = 280.0, proteinG = 9.6),
        servingSizeText = "80 g",
        servingGrams = 80.0,
    )

    private fun data(
        overrides: List<BackupRecipeOverride> = emptyList(),
        facts: List<BackupLabelNutrition> = emptyList(),
    ) = BackupData(
        settings = HouseholdSettings(householdSize = 4, onboardingComplete = true),
        priceItems = book,
        pantryItems = listOf(PantryItem("salt", "Salt", owned = true)),
        bulkStock = emptyList(),
        recipeOverrides = overrides,
        labelNutrition = facts,
    )

    @Test fun labelFactsAndOverrideStepsRoundTrip() {
        val override = BackupRecipeOverride(
            recipeId = "spaghetti-night",
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE),
                ),
            ),
            steps = listOf("Boil.", "Sauce.", "Serve."),
        )
        val original = data(overrides = listOf(override), facts = listOf(spaghettiFacts))
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(9, restored.version)
        assertEquals(listOf(spaghettiFacts), restored.labelNutrition)
        assertEquals(listOf("Boil.", "Sauce.", "Serve."), restored.recipeOverrides.single().steps)
    }

    @Test fun versionEightBackupImportsWithNoFactsAndNoSteps() {
        // A real v8 file predates both fields entirely.
        val override = BackupRecipeOverride(
            recipeId = "spaghetti-night",
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE),
                ),
            ),
        )
        val exported = BackupEngine.export(data(overrides = listOf(override)))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v8 = JsonObject(parsed - "labelNutrition" + ("version" to JsonPrimitive(8)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v8))
        assertEquals(8, restored.version)
        assertTrue(restored.labelNutrition.isEmpty())
        assertNull(restored.recipeOverrides.single().steps)
    }

    @Test fun duplicateLabelFactsAreRejected() {
        val dup = data(facts = listOf(spaghettiFacts, spaghettiFacts))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(dup))
        }
    }

    @Test fun factsForAnItemOutsideTheBookAreRejected() {
        val broken = data(facts = listOf(spaghettiFacts.copy(itemId = "not-in-the-book")))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(broken))
        }
    }

    @Test fun overrideWithEmptyStepsIsRejected() {
        val broken = BackupRecipeOverride(
            recipeId = "spaghetti-night",
            ingredients = listOf(
                RecipeIngredient(
                    priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE),
                ),
            ),
            steps = emptyList(),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(data(overrides = listOf(broken))))
        }
    }
}
