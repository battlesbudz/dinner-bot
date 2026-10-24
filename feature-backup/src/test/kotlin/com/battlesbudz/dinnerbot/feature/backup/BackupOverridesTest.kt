package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
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
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase RI-1: household recipe edits ride in backup format v6, so a
 * phone swap never silently loses the corrections Andrea made forever.
 * Files from every earlier format still import, with no overrides.
 */
class BackupOverridesTest {

    private fun item(id: String, name: String) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = Store.ALDI, packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-23", source = "test",
    )

    private val book = listOf(item("ground-turkey", "Ground turkey"), item("spaghetti", "Spaghetti"))

    private fun data(overrides: List<BackupRecipeOverride>) = BackupData(
        settings = HouseholdSettings(householdSize = 4, onboardingComplete = true),
        priceItems = book,
        pantryItems = listOf(PantryItem("salt", "Salt", owned = true)),
        bulkStock = emptyList(),
        recipeOverrides = overrides,
    )

    private val herSpaghetti = BackupRecipeOverride(
        recipeId = "turkey-spaghetti",
        ingredients = listOf(
            RecipeIngredient(
                priceItemId = "ground-turkey", quantity = Quantity(2.0, MeasureUnit.POUND),
                essential = true,
            ),
            RecipeIngredient(
                pantryItem = "Soy sauce", quantity = Quantity(1.0, MeasureUnit.TABLESPOON),
                note = "her way", essential = false,
            ),
        ),
    )

    @Test fun overridesRoundTripThroughExportImport() {
        val original = data(listOf(herSpaghetti))
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(6, restored.version)
        assertEquals(BackupData.CURRENT_VERSION, restored.version)
        assertEquals(original.recipeOverrides, restored.recipeOverrides)
    }

    @Test fun versionFiveBackupImportsWithNoOverrides() {
        // A real v5 file predates the recipeOverrides field entirely.
        val exported = BackupEngine.export(data(listOf(herSpaghetti)))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v5 = JsonObject(parsed - "recipeOverrides" + ("version" to JsonPrimitive(5)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v5))
        assertTrue(restored.recipeOverrides.isEmpty())
    }

    @Test fun versionOneBackupStillImports() {
        val exported = BackupEngine.export(data(emptyList()))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v1 = JsonObject(
            parsed - "recipeRatings" - "barcodeLinks" - "ranOutEvents" -
                "cookCheckoffs" - "scanSubstitutions" - "recipeOverrides" +
                ("version" to JsonPrimitive(1)),
        )
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v1))
        assertTrue(restored.recipeOverrides.isEmpty())
    }

    @Test fun duplicateOverrideForOneRecipeIsRejected() {
        val bad = data(listOf(herSpaghetti, herSpaghetti))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(bad))
        }
    }

    @Test fun overrideUsingAnItemOutsideTheBackupsBookIsRejected() {
        val bad = data(
            listOf(
                BackupRecipeOverride(
                    recipeId = "turkey-spaghetti",
                    ingredients = listOf(
                        RecipeIngredient(
                            priceItemId = "not-in-the-book",
                            quantity = Quantity(1.0, MeasureUnit.POUND),
                        ),
                    ),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(bad))
        }
    }

    @Test fun overrideWithNoIngredientsIsRejected() {
        val bad = data(listOf(BackupRecipeOverride(recipeId = "turkey-spaghetti", ingredients = emptyList())))
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(bad))
        }
    }
}
