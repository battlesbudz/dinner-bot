package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6.3: scanned label facts (Open Food Facts, per barcode) win over the
 * bundled USDA entry for the ingredient whose item carries them, and
 * only for that ingredient. Expected numbers are hand-computed from the
 * fixture label values, so a wrong preference or conversion fails loudly.
 */
class LabelNutritionTest {

    private fun usda(
        id: String,
        values: NutritionValues,
        gramWeights: Map<String, Double> = emptyMap(),
    ) = IngredientNutrition(
        itemId = id,
        fdcId = 1,
        fdcDescription = "fixture USDA",
        source = "fixture",
        per100g = values,
        gramWeights = gramWeights,
        gramWeightSource = if (gramWeights.isEmpty()) null else "fixture portions",
    )

    // USDA pasta: 370 kcal per 100 g. The scanned box says 350.
    private val pastaUsda = usda(
        "spaghetti",
        NutritionValues(caloriesKcal = 370.0, proteinG = 13.0, fatG = 1.5),
    )
    private val pastaLabel = LabelNutrition(
        itemId = "spaghetti",
        barcode = "3017620422003",
        per100g = NutritionValues(caloriesKcal = 350.0, proteinG = 12.0, fatG = 2.0),
        perServing = NutritionValues(caloriesKcal = 280.0, proteinG = 9.6, fatG = 1.6),
        servingSizeText = "80 g",
        servingGrams = 80.0,
    )

    // USDA eggs: 143 kcal per 100 g, 50 g per egg. The scanned carton
    // says 155 per 100 g and publishes no per-serving set.
    private val eggsUsda = usda(
        "eggs",
        NutritionValues(caloriesKcal = 143.0, proteinG = 12.56),
        gramWeights = mapOf("COUNT" to 50.0),
    )
    private val eggsLabelNoServing = LabelNutrition(
        itemId = "eggs",
        barcode = "0023456789012",
        per100g = NutritionValues(caloriesKcal = 155.0, proteinG = 13.0),
        servingSizeText = "1 egg (50 g)",
        servingGrams = 50.0,
    )

    private val byItemId = listOf(pastaUsda, eggsUsda).associateBy { it.itemId }

    private fun dinner(vararg ingredients: RecipeIngredient) = Recipe(
        id = "label-dinner",
        title = "Label Dinner",
        servings = 4,
        activeMinutes = 10,
        cuisine = "Test",
        mainProtein = "Eggs",
        ingredients = ingredients.toList(),
        steps = listOf("Cook."),
    )

    @Test fun weightLineUsesLabelFactsNotUsda() {
        // 8 oz spaghetti = 226.796 g. Label: 350 kcal/100 g -> 793.79.
        // USDA would give 839.15, so the label number proves the source.
        val recipe = dinner(
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId, emptyMap(), mapOf("spaghetti" to pastaLabel),
        )
        assertEquals(793.79, nutrition.totals.caloriesKcal!!, 0.5)
        assertTrue(nutrition.labelFactsUsed)
        assertFalse(nutrition.closestMatchUsed)
    }

    @Test fun unscannedIngredientStillUsesUsda() {
        // No label facts in the map at all: the USDA math stands alone.
        val recipe = dinner(
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, emptyMap())
        assertEquals(839.15, nutrition.totals.caloriesKcal!!, 0.5)
        assertFalse(nutrition.labelFactsUsed)
    }

    @Test fun countLineUsesPerServingFactsWhenPublished() {
        // 3 spaghetti "servings" by count reads the per-serving set:
        // 3 x 280 kcal = 840.
        val recipe = dinner(
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(3.0, MeasureUnit.COUNT)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId, emptyMap(), mapOf("spaghetti" to pastaLabel),
        )
        assertEquals(840.0, nutrition.totals.caloriesKcal!!, 0.01)
        assertTrue(nutrition.labelFactsUsed)
    }

    @Test fun countLineFallsBackToServingGramsBridge() {
        // No per-serving set on the egg label: 2 eggs x 50 g serving =
        // 100 g at 155 kcal/100 g = 155 kcal (USDA would give 143).
        val recipe = dinner(
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity(2.0, MeasureUnit.COUNT)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId, emptyMap(), mapOf("eggs" to eggsLabelNoServing),
        )
        assertEquals(155.0, nutrition.totals.caloriesKcal!!, 0.01)
        assertTrue(nutrition.labelFactsUsed)
    }

    @Test fun unconvertibleLabelLineFallsBackToUsdaForThatLine() {
        // A volume line has no honest bridge from a label (no density):
        // the USDA entry for the line must carry it instead.
        val milkUsda = usda(
            "milk",
            NutritionValues(caloriesKcal = 42.0, proteinG = 3.4),
            gramWeights = mapOf("CUP" to 240.0),
        )
        val milkLabel = LabelNutrition(
            itemId = "milk",
            barcode = "0070387700005",
            per100g = NutritionValues(caloriesKcal = 64.0, proteinG = 3.3),
            servingSizeText = "240 ml",
            servingGrams = null,
        )
        val recipe = dinner(
            RecipeIngredient(priceItemId = "milk", quantity = Quantity(1.0, MeasureUnit.CUP)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, mapOf("milk" to milkUsda), emptyMap(), mapOf("milk" to milkLabel),
        )
        // USDA: 240 g at 42 kcal/100 g = 100.8 kcal.
        assertEquals(100.8, nutrition.totals.caloriesKcal!!, 0.01)
        assertFalse(nutrition.labelFactsUsed)
    }

    @Test fun labelFieldTheLabelOmitsStaysIncomplete() {
        // The pasta label states no sodium: sodium is partial, in words,
        // exactly like a USDA field FoodData Central does not publish.
        val recipe = dinner(
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE)),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId, emptyMap(), mapOf("spaghetti" to pastaLabel),
        )
        assertTrue(NutritionField.SODIUM in nutrition.incompleteFields)
        assertTrue(nutrition.isPartial)
    }

    @Test fun sumCarriesTheLabelFlag() {
        val recipe = dinner(
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE)),
        )
        val withLabel = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId, emptyMap(), mapOf("spaghetti" to pastaLabel),
        )
        val plain = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, emptyMap())
        assertTrue(NutritionCalculator.sum(listOf(withLabel, plain)).labelFactsUsed)
        assertFalse(NutritionCalculator.sum(listOf(plain)).labelFactsUsed)
    }

    @Test fun stepsOverrideStandsInForSeedSteps() {
        val seed = dinner(
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity(2.0, MeasureUnit.COUNT)),
        ).copy(steps = listOf("Seed step one.", "Seed step two."))
        val override = RecipeOverride(
            recipeId = seed.id,
            ingredients = seed.ingredients,
            updatedAtMillis = 5L,
            steps = listOf("Her step."),
        )
        val effective = EffectiveRecipes.applyOverride(seed, override)
        assertEquals(listOf("Her step."), effective.steps)
        assertEquals(seed.ingredients, effective.ingredients)
    }

    @Test fun ingredientsOnlyOverrideLeavesSeedStepsAlone() {
        val seed = dinner(
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity(2.0, MeasureUnit.COUNT)),
        ).copy(steps = listOf("Seed step."))
        val override = RecipeOverride(
            recipeId = seed.id,
            ingredients = emptyList(),
            updatedAtMillis = 5L,
        )
        val effective = EffectiveRecipes.applyOverride(seed, override)
        assertEquals(listOf("Seed step."), effective.steps)
        assertEquals(emptyList<RecipeIngredient>(), effective.ingredients)
    }
}
