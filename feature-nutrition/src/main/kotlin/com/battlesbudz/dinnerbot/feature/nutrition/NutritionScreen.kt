package com.battlesbudz.dinnerbot.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionCalculator
import com.battlesbudz.dinnerbot.core.model.NutritionField
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeNutrition

/** The fields the tab and recipe screen lead with, in reading order. */
val DISPLAY_FIELDS: List<NutritionField> = listOf(
    NutritionField.CALORIES,
    NutritionField.PROTEIN,
    NutritionField.FAT,
    NutritionField.CARBOHYDRATE,
    NutritionField.FIBER,
    NutritionField.SODIUM,
)

const val NUTRITION_ESTIMATE_LABEL =
    "Estimates from USDA FoodData Central data, worked out from each recipe's ingredients. " +
        "Packaged foods are matched to the closest USDA entry, so treat these numbers as a " +
        "guide, not a lab reading."

/**
 * The Nutrition tab (Phase V2-6): the planned week's totals and every
 * recipe's per-serving figures side by side for comparison. Display only:
 * nothing here feeds the planner, the budget, or any goal.
 */
@Composable
fun NutritionScreen(
    recipes: List<Recipe>,
    byItemId: Map<String, IngredientNutrition>,
    byPantrySlug: Map<String, IngredientNutrition>,
    weeks: List<NutritionWeekInput>,
    householdSize: Int,
    modifier: Modifier = Modifier,
) {
    val recipesById = remember(recipes) { recipes.associateBy { it.id } }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Nutrition", style = MaterialTheme.typography.headlineMedium)
            Text(NUTRITION_ESTIMATE_LABEL, style = MaterialTheme.typography.bodyMedium)
        }
        if (weeks.isEmpty()) {
            item {
                Text(
                    "Plan a week and its dinners' nutrition will add up here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        weeks.forEach { week ->
            item(key = "week-${week.label}") {
                val weekNutrition = remember(week, recipesById, householdSize, byItemId, byPantrySlug) {
                    WeekNutrition.forWeek(week.nights, recipesById, householdSize, byItemId, byPantrySlug)
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(week.label, style = MaterialTheme.typography.titleMedium)
                        if (weekNutrition == null) {
                            Text(
                                "No dinners are cooking this week.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Text(
                                "Added up per night: ${headlineLine(weekNutrition.totals)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "Per serving, each night added up:",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            NutritionDetailLines(weekNutrition, weekNutrition.perServing)
                        }
                    }
                }
            }
        }
        item {
            Text("Every recipe, per serving", style = MaterialTheme.typography.titleMedium)
        }
        items(recipes, key = { it.id }) { recipe ->
            val nutrition = remember(recipe, byItemId, byPantrySlug) {
                NutritionCalculator.recipeNutrition(recipe, recipe.servings, byItemId, byPantrySlug)
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(recipe.title, style = MaterialTheme.typography.titleMedium)
                    Text(headlineLine(nutrition.perServing), style = MaterialTheme.typography.bodyMedium)
                    NutritionNotes(nutrition)
                }
            }
        }
    }
}

/** "512 kcal · 24 g protein · 18 g fat · 58 g carbs" from the four headline fields. */
fun headlineLine(values: NutritionValues): String {
    val kcal = NutritionFormat.amount(values.caloriesKcal, NutritionField.CALORIES)
    val protein = NutritionFormat.amount(values.proteinG, NutritionField.PROTEIN)
    val fat = NutritionFormat.amount(values.fatG, NutritionField.FAT)
    val carbs = NutritionFormat.amount(values.carbohydrateG, NutritionField.CARBOHYDRATE)
    return "$kcal · $protein protein · $fat fat · $carbs carbs"
}

/** The per-field lines plus the honest notes (partial data, closest matches). */
@Composable
fun NutritionDetailLines(nutrition: RecipeNutrition, values: NutritionValues) {
    DISPLAY_FIELDS.forEach { field ->
        val marker = if (field in nutrition.incompleteFields) " (partial)" else ""
        Text(
            "${field.label}: ${NutritionFormat.amount(values.value(field), field)}$marker",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    NutritionNotes(nutrition)
}

@Composable
fun NutritionNotes(nutrition: RecipeNutrition) {
    if (nutrition.isPartial) {
        Text(
            NutritionFormat.partialNote(nutrition.incompleteFields),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (nutrition.closestMatchUsed) {
        Text(
            "Includes packaged foods matched to the closest USDA entry.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
