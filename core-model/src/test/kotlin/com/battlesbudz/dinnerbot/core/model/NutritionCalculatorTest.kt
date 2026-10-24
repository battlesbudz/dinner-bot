package com.battlesbudz.dinnerbot.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase V2-6 computation fixtures. Expected numbers are hand-computed from
 * the fixture entries' per-100 g values with the unit system's own factors
 * (1 lb = 16 oz, grams = ounces / 0.035274), so a wrong conversion or a
 * wrong incomplete rule fails loudly here.
 */
class NutritionCalculatorTest {

    private fun entry(
        id: String,
        pantry: Boolean = false,
        values: NutritionValues,
        gramWeights: Map<String, Double> = emptyMap(),
        closestMatch: Boolean = false,
    ) = IngredientNutrition(
        itemId = id,
        pantryItem = pantry,
        fdcId = 1,
        fdcDescription = "fixture",
        source = "fixture",
        per100g = values,
        gramWeights = gramWeights,
        gramWeightSource = if (gramWeights.isEmpty()) null else "fixture portions",
        closestMatch = closestMatch,
    )

    private val turkey = entry(
        "ground-turkey",
        values = NutritionValues(
            caloriesKcal = 150.0, proteinG = 20.0, fatG = 7.0, carbohydrateG = 0.0,
            fiberG = null, sugarsG = 0.0, sodiumMg = 60.0, saturatedFatG = 2.0,
            cholesterolMg = null,
        ),
    )
    private val pasta = entry(
        "spaghetti",
        values = NutritionValues(
            caloriesKcal = 370.0, proteinG = 13.0, fatG = 1.5, carbohydrateG = 75.0,
            fiberG = 3.0, sugarsG = 2.7, sodiumMg = 5.0, saturatedFatG = 0.3,
            cholesterolMg = 0.0,
        ),
    )
    private val eggs = entry(
        "eggs",
        values = NutritionValues(
            caloriesKcal = 143.0, proteinG = 12.56, fatG = 9.51, carbohydrateG = 0.72,
            fiberG = 0.0, sugarsG = null, sodiumMg = 142.0, saturatedFatG = 3.1,
            cholesterolMg = 372.0,
        ),
        gramWeights = mapOf("COUNT" to 50.0),
    )
    private val oil = entry(
        "vegetable-oil",
        pantry = true,
        values = NutritionValues(
            caloriesKcal = 884.0, proteinG = 0.0, fatG = 100.0, carbohydrateG = 0.0,
            fiberG = 0.0, sugarsG = null, sodiumMg = 0.0, saturatedFatG = 13.7,
            cholesterolMg = 0.0,
        ),
        gramWeights = mapOf("TABLESPOON" to 13.6),
    )
    private val salt = entry(
        "salt",
        pantry = true,
        values = NutritionValues(
            caloriesKcal = 0.0, proteinG = 0.0, fatG = 0.0, carbohydrateG = 0.0,
            fiberG = 0.0, sugarsG = null, sodiumMg = 38758.0, saturatedFatG = 0.0,
            cholesterolMg = 0.0,
        ),
        gramWeights = mapOf("TEASPOON" to 6.0),
    )

    private val byItemId = listOf(turkey, pasta, eggs).associateBy { it.itemId }
    private val byPantrySlug = listOf(oil, salt).associateBy { it.itemId }

    private val recipe = Recipe(
        id = "fixture-dinner",
        title = "Fixture Dinner",
        servings = 4,
        activeMinutes = 10,
        cuisine = "Test",
        mainProtein = "Ground turkey",
        ingredients = listOf(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity(1.0, MeasureUnit.POUND)),
            RecipeIngredient(priceItemId = "spaghetti", quantity = Quantity(8.0, MeasureUnit.OUNCE)),
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity(2.0, MeasureUnit.COUNT)),
            RecipeIngredient(
                pantryItem = "Vegetable oil",
                quantity = Quantity(1.0, MeasureUnit.TABLESPOON),
                essential = false,
            ),
            RecipeIngredient(pantryItem = "Salt", quantity = Quantity(1.0, MeasureUnit.TEASPOON)),
        ),
        steps = listOf("Cook."),
    )

    @Test fun handComputedPerServingFigures() {
        val nutrition = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, byPantrySlug)
        assertEquals(445.69, nutrition.perServing.caloriesKcal!!, 0.6)
        assertEquals(33.19, nutrition.perServing.proteinG!!, 0.6)
        assertEquals(14.57, nutrition.perServing.fatG!!, 0.6)
        assertEquals(42.70, nutrition.perServing.carbohydrateG!!, 0.6)
        assertEquals(687.74, nutrition.perServing.sodiumMg!!, 0.6)
        assertEquals(1782.76, nutrition.totals.caloriesKcal!!, 0.6)
    }

    @Test fun pricedStapleResolvesThroughTheItemMap() {
        // v0.4.3: the staples are priced items, so a recipe referencing
        // salt by price item id resolves its entry through byItemId (the
        // entry's pantry flag is false now), with the sourced gram weight
        // doing the teaspoon conversion exactly as the pantry path did.
        val pricedSalt = entry(
            "salt",
            values = NutritionValues(
                caloriesKcal = 0.0, proteinG = 0.0, fatG = 0.0, carbohydrateG = 0.0,
                fiberG = 0.0, sugarsG = 0.0, sodiumMg = 38758.0, saturatedFatG = 0.0,
                cholesterolMg = 0.0,
            ),
            gramWeights = mapOf("TEASPOON" to 6.0),
        )
        val stapleRecipe = Recipe(
            id = "salted-dinner",
            title = "Salted Dinner",
            servings = 4,
            activeMinutes = 10,
            cuisine = "Test",
            mainProtein = "Egg",
            ingredients = listOf(
                RecipeIngredient(priceItemId = "salt", quantity = Quantity(1.0, MeasureUnit.TEASPOON)),
            ),
            steps = listOf("Cook."),
        )
        val nutrition = NutritionCalculator.recipeNutrition(
            stapleRecipe, 4, mapOf("salt" to pricedSalt), emptyMap(),
        )
        // 1 tsp = 6.0 g; sodium 38758 mg per 100 g -> 2325.48 mg total.
        assertEquals(2325.48, nutrition.totals.sodiumMg!!, 0.6)
        assertEquals(581.37, nutrition.perServing.sodiumMg!!, 0.6)
        assertTrue(nutrition.incompleteFields.isEmpty())
    }

    @Test fun nullFieldsMarkOnlyThoseFieldsIncomplete() {
        val nutrition = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, byPantrySlug)
        // Turkey publishes no fiber or cholesterol; eggs, oil, and salt
        // publish no sugars. Every other field has a value everywhere.
        assertEquals(
            setOf(NutritionField.FIBER, NutritionField.SUGARS, NutritionField.CHOLESTEROL),
            nutrition.incompleteFields,
        )
        // A partial total is still the sum of what IS sourced, not null.
        assertEquals(1.70, nutrition.perServing.fiberG!!, 0.6)
        assertFalse(nutrition.incompleteFields.contains(NutritionField.CALORIES))
    }

    @Test fun scalingDoublesTotalsAndKeepsPerServing() {
        val base = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, byPantrySlug)
        val doubled = NutritionCalculator.recipeNutrition(recipe, 8, byItemId, byPantrySlug)
        assertEquals(base.totals.caloriesKcal!! * 2, doubled.totals.caloriesKcal!!, 0.01)
        assertEquals(base.perServing.caloriesKcal!!, doubled.perServing.caloriesKcal!!, 0.01)
        assertEquals(base.perServing.sodiumMg!!, doubled.perServing.sodiumMg!!, 0.01)
    }

    @Test fun missingEntryMarksEveryFieldIncomplete() {
        val nutrition = NutritionCalculator.recipeNutrition(recipe, 4, emptyMap(), byPantrySlug)
        assertEquals(NutritionField.entries.toSet(), nutrition.incompleteFields)
        // The pantry staples still contribute, so totals are partial sums.
        assertEquals(120.24, nutrition.totals.caloriesKcal!!, 0.6)
    }

    @Test fun countWithoutGramWeightMarksEveryFieldIncomplete() {
        val eggsNoWeight = eggs.copy(gramWeights = emptyMap(), gramWeightSource = null)
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId + ("eggs" to eggsNoWeight), byPantrySlug,
        )
        assertEquals(NutritionField.entries.toSet(), nutrition.incompleteFields)
        // The eggs' 143 kcal per 100 g never lands: totals drop by 143.
        assertEquals(445.69 - 143.0 / 4, nutrition.perServing.caloriesKcal!!, 0.6)
    }

    @Test fun gramWeightComesFromTheEntry() {
        val grams = NutritionCalculator.gramsFor(
            RecipeIngredient(priceItemId = "eggs", quantity = Quantity(3.0, MeasureUnit.COUNT)),
            eggs,
        )
        assertEquals(150.0, grams!!, 0.001)
        val weightGrams = NutritionCalculator.gramsFor(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity(1.0, MeasureUnit.POUND)),
            turkey,
        )
        assertEquals(453.59, weightGrams!!, 0.01)
        assertNull(
            NutritionCalculator.gramsFor(
                RecipeIngredient(priceItemId = "eggs", quantity = Quantity(1.0, MeasureUnit.COUNT)),
                eggs.copy(gramWeights = emptyMap()),
            ),
        )
    }

    @Test fun wontEatAdaptationChangesTheFigures() {
        val item = PriceItem(
            id = "ground-turkey", name = "Ground turkey", category = "Meat",
            homeStore = Store.WALMART, packDescription = "pack",
            packQuantity = Quantity.pounds(1.0), packPriceCents = 198,
            checkedDate = "2026-10-22", source = "fixture",
        )
        val adapted = RecipeAdaptation.adapt(
            listOf(recipe), listOf("vegetable oil"), mapOf("ground-turkey" to item),
        ).recipes.single()
        val nutrition = NutritionCalculator.recipeNutrition(adapted, 4, byItemId, byPantrySlug)
        // The oil is out of the dish, so its 13.6 g of fat is out of the
        // figures: fat per serving drops from 14.57 to 11.17.
        assertEquals(11.17, nutrition.perServing.fatG!!, 0.6)
        assertEquals(415.63, nutrition.perServing.caloriesKcal!!, 0.6)
    }

    @Test fun sumAddsTotalsAndPerServingAndUnionsIncomplete() {
        val one = NutritionCalculator.recipeNutrition(recipe, 4, byItemId, byPantrySlug)
        val week = NutritionCalculator.sum(listOf(one, one, one))
        assertEquals(one.totals.caloriesKcal!! * 3, week.totals.caloriesKcal!!, 0.01)
        assertEquals(one.perServing.caloriesKcal!! * 3, week.perServing.caloriesKcal!!, 0.01)
        assertEquals(one.incompleteFields, week.incompleteFields)
    }

    @Test fun closestMatchIsCarriedThrough() {
        val matched = pasta.copy(closestMatch = true)
        val nutrition = NutritionCalculator.recipeNutrition(
            recipe, 4, byItemId + ("spaghetti" to matched), byPantrySlug,
        )
        assertTrue(nutrition.closestMatchUsed)
        assertTrue(NutritionCalculator.sum(listOf(nutrition)).closestMatchUsed)
    }
}
