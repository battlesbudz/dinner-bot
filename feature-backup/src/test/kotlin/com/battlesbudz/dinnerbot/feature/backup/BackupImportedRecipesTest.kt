package com.battlesbudz.dinnerbot.feature.backup

import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.RecipeOrigin
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
 * Phase RI-2: Andrea's own recipes ride in backup format v7, so a
 * phone swap keeps the recipes she brought in and wrote herself.
 * Files from every earlier format still import, with none.
 */
class BackupImportedRecipesTest {

    private fun item(id: String, name: String) = PriceItem(
        id = id, name = name, category = "Test",
        homeStore = "aldi", packDescription = "pack",
        packQuantity = Quantity(1.0, MeasureUnit.POUND), packPriceCents = 100,
        pricePerUnitCents = 100.0, checkedDate = "2026-10-24", source = "test",
    )

    private val book = listOf(item("chicken-breast", "Chicken breast"))

    private val herImport = BackupImportedRecipe(
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
                    essential = true,
                ),
            ),
            steps = listOf("Sear.", "Serve."),
            origin = RecipeOrigin.IMPORTED,
        ),
        sourceLink = "https://example.com/lemon-chicken",
        createdAtMillis = 123456789L,
    )

    private fun data(imported: List<BackupImportedRecipe>) = BackupData(
        settings = HouseholdSettings(householdSize = 4, onboardingComplete = true),
        priceItems = book,
        pantryItems = listOf(PantryItem("salt", "Salt", owned = true)),
        bulkStock = emptyList(),
        importedRecipes = imported,
    )

    @Test fun importedRecipesRoundTripThroughExportImport() {
        val original = data(listOf(herImport))
        val restored = BackupEngine.import(BackupEngine.export(original))
        assertEquals(9, restored.version)
        assertEquals(BackupData.CURRENT_VERSION, restored.version)
        assertEquals(original.importedRecipes, restored.importedRecipes)
    }

    @Test fun versionSixBackupImportsWithNoImportedRecipes() {
        // A real v6 file predates the importedRecipes field entirely.
        val exported = BackupEngine.export(data(listOf(herImport)))
        val parsed = Json.parseToJsonElement(exported).jsonObject
        val v6 = JsonObject(parsed - "importedRecipes" + ("version" to JsonPrimitive(6)))
        val restored = BackupEngine.import(Json.encodeToString(JsonObject.serializer(), v6))
        assertTrue(restored.importedRecipes.isEmpty())
    }

    @Test fun duplicateImportedIdsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(
                BackupEngine.export(data(listOf(herImport, herImport))),
            )
        }
    }

    @Test fun aLibraryOriginInTheImportedSectionIsRejected() {
        val disguised = herImport.copy(
            recipe = herImport.recipe.copy(origin = RecipeOrigin.LIBRARY),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(data(listOf(disguised))))
        }
    }

    @Test fun anImportedRecipePricingAnUnknownBookItemIsRejected() {
        val broken = herImport.copy(
            recipe = herImport.recipe.copy(
                ingredients = listOf(
                    RecipeIngredient(
                        priceItemId = "not-in-the-book",
                        quantity = Quantity(1.0, MeasureUnit.POUND),
                        essential = true,
                    ),
                ),
            ),
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupEngine.import(BackupEngine.export(data(listOf(broken))))
        }
    }

    @Test fun guessMarksRideTheBackup() {
        // Battles's correction (2026-10-24): an import-created item's
        // guess mark is household state — a phone swap must not turn a
        // guessed price into a silently trusted one.
        val guessed = book.first().copy(guessPriced = true)
        val withGuess = BackupData(
            settings = HouseholdSettings(householdSize = 4, onboardingComplete = true),
            priceItems = listOf(guessed),
            pantryItems = emptyList(),
            bulkStock = emptyList(),
        )
        val restored = BackupEngine.import(BackupEngine.export(withGuess))
        assertEquals(9, restored.version)
        assertEquals(true, restored.priceItems.single().guessPriced)
    }
}
