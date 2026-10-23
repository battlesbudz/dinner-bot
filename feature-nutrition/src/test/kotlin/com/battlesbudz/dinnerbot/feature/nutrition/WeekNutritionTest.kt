package com.battlesbudz.dinnerbot.feature.nutrition

import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NutritionCalculator
import com.battlesbudz.dinnerbot.core.model.NutritionField
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase V2-6: week totals over planned nights, and the display formatting. */
class WeekNutritionTest {

    private val turkeyEntry = IngredientNutrition(
        itemId = "ground-turkey",
        fdcId = 1,
        fdcDescription = "fixture",
        source = "fixture",
        per100g = NutritionValues(
            caloriesKcal = 150.0, proteinG = 20.0, fatG = 7.0, carbohydrateG = 0.0,
            fiberG = 0.0, sugarsG = 0.0, sodiumMg = 60.0, saturatedFatG = 2.0,
            cholesterolMg = 100.0,
        ),
    )
    private val byItemId = mapOf("ground-turkey" to turkeyEntry)

    private fun dinner(id: String) = Recipe(
        id = id,
        title = id,
        servings = 4,
        activeMinutes = 10,
        cuisine = "Test",
        mainProtein = "Ground turkey",
        ingredients = listOf(
            RecipeIngredient(priceItemId = "ground-turkey", quantity = Quantity(1.0, MeasureUnit.POUND)),
        ),
        steps = listOf("Cook."),
    )

    @Test fun weekTotalsSumTheCookedNights() {
        val recipes = mapOf("a" to dinner("a"), "b" to dinner("b"))
        val nights = listOf(
            PlannedNight(day = "MON", recipeId = "a"),
            PlannedNight(day = "TUE", recipeId = "b"),
            // A night off cooks nothing and contributes nothing.
            PlannedNight(day = "WED", recipeId = "a", notCooking = true, notCookingReason = "leftovers"),
            // A night whose recipe is gone contributes nothing either.
            PlannedNight(day = "THU", recipeId = "gone"),
        )
        val week = WeekNutrition.forWeek(nights, recipes, householdSize = 4, byItemId, emptyMap())
        val oneNight = NutritionCalculator.recipeNutrition(dinner("a"), 4, byItemId, emptyMap())
        assertEquals(oneNight.totals.caloriesKcal!! * 2, week!!.totals.caloriesKcal!!, 0.01)
        assertEquals(oneNight.perServing.caloriesKcal!! * 2, week.perServing.caloriesKcal!!, 0.01)
        assertTrue(week.incompleteFields.isEmpty())
    }

    @Test fun weekWithNoCookedNightsIsNull() {
        val nights = listOf(PlannedNight(day = "MON", recipeId = "a", notCooking = true))
        assertNull(WeekNutrition.forWeek(nights, emptyMap(), 4, byItemId, emptyMap()))
        assertNull(WeekNutrition.forWeek(emptyList(), emptyMap(), 4, byItemId, emptyMap()))
    }

    @Test fun formattingRoundsAndLabels() {
        assertEquals("446 kcal", NutritionFormat.amount(445.69, NutritionField.CALORIES))
        assertEquals("14.6 g", NutritionFormat.amount(14.5658, NutritionField.FAT))
        assertEquals("20 g", NutritionFormat.amount(20.0, NutritionField.PROTEIN))
        assertEquals("688 mg", NutritionFormat.amount(687.74, NutritionField.SODIUM))
        assertEquals("no data", NutritionFormat.amount(null, NutritionField.FIBER))
    }

    @Test fun partialNoteNamesTheFields() {
        val note = NutritionFormat.partialNote(setOf(NutritionField.SUGARS, NutritionField.FIBER))
        assertTrue(note.contains("fiber"))
        assertTrue(note.contains("sugars"))
        assertTrue(note.contains("Partial data"))
    }
}
