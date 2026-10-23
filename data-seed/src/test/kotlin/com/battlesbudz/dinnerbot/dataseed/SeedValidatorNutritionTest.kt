package com.battlesbudz.dinnerbot.dataseed

import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import com.battlesbudz.dinnerbot.core.model.Store
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-6: the nutrition honesty rules, as validator cases. */
class SeedValidatorNutritionTest {

    private fun item(id: String) = PriceItem(
        id = id, name = id, category = "Meat", homeStore = Store.WALMART,
        packDescription = "pack", packQuantity = Quantity.pounds(1.0),
        packPriceCents = 100, checkedDate = "2026-10-22", source = "test",
    )

    private fun recipe() = Recipe(
        id = "r1", title = "R", servings = 4, activeMinutes = 5, cuisine = "American",
        mainProtein = "Ground turkey",
        ingredients = listOf(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity.pounds(1.0)),
            RecipeIngredient(pantryItem = "Salt", quantity = Quantity(1.0, com.battlesbudz.dinnerbot.core.model.MeasureUnit.TEASPOON)),
        ),
        steps = listOf("Cook."),
    )

    private fun entry(
        id: String,
        pantry: Boolean = false,
        calories: Double? = 100.0,
        source: String = "USDA FoodData Central (fdcId 1)",
        gramWeights: Map<String, Double> = emptyMap(),
        gramWeightSource: String? = null,
    ) = IngredientNutrition(
        itemId = id,
        pantryItem = pantry,
        fdcId = 1,
        fdcDescription = "fixture",
        source = source,
        per100g = NutritionValues(caloriesKcal = calories),
        gramWeights = gramWeights,
        gramWeightSource = gramWeightSource,
    )

    private val turkeyEntry = entry("ground-turkey")
    private val saltEntry = entry("salt", pantry = true)

    private fun validate(
        entries: List<IngredientNutrition>,
        incomplete: List<String> = emptyList(),
    ) = SeedValidator.validateNutrition(entries, listOf(item("ground-turkey")), listOf(recipe()), incomplete)

    @Test fun completeCoveragePasses() {
        assertTrue(validate(listOf(turkeyEntry, saltEntry)).isEmpty())
    }

    @Test fun missingEntryNotOnTheListFails() {
        val errors = validate(listOf(turkeyEntry))
        assertTrue(errors.any { it.contains("salt") && it.contains("known-incomplete") })
    }

    @Test fun missingEntryOnTheListPasses() {
        assertTrue(validate(listOf(turkeyEntry), incomplete = listOf("salt")).isEmpty())
    }

    @Test fun staleIncompleteNameFails() {
        val errors = validate(listOf(turkeyEntry, saltEntry), incomplete = listOf("salt"))
        assertTrue(errors.any { it.contains("which has a nutrition entry") })
        val unknown = validate(listOf(turkeyEntry, saltEntry), incomplete = listOf("mystery"))
        assertTrue(unknown.any { it.contains("no recipe uses") })
    }

    @Test fun entryWithoutCaloriesOrSourceFails() {
        val noCalories = validate(listOf(turkeyEntry.copy(per100g = NutritionValues()), saltEntry))
        assertTrue(noCalories.any { it.contains("no calories") })
        val noSource = validate(listOf(entry("ground-turkey", source = ""), saltEntry))
        assertTrue(noSource.any { it.contains("missing source") })
    }

    @Test fun gramWeightRulesAreEnforced() {
        val weightUnit = validate(
            listOf(turkeyEntry, saltEntry.copy(gramWeights = mapOf("OUNCE" to 28.0), gramWeightSource = "x")),
        )
        assertTrue(weightUnit.any { it.contains("weight unit") })
        val noSource = validate(
            listOf(turkeyEntry, saltEntry.copy(gramWeights = mapOf("TEASPOON" to 6.0))),
        )
        assertTrue(noSource.any { it.contains("without a gram weight source") })
        val good = validate(
            listOf(
                turkeyEntry,
                saltEntry.copy(gramWeights = mapOf("TEASPOON" to 6.0), gramWeightSource = "FDC portions"),
            ),
        )
        assertTrue(good.isEmpty())
    }
}
