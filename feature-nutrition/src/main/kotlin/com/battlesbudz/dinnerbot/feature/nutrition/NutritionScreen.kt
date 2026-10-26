package com.battlesbudz.dinnerbot.feature.nutrition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionField
import com.battlesbudz.dinnerbot.core.model.NutritionValues
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeNutrition

/** The fields the week card and recipe screen lead with, in reading order. */
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
 * The week's nutrition as a card on the Plan screen (v0.6.0 redesign):
 * the Nutrition tab's weekly summary, folded into the plan it describes.
 * Per-recipe figures live on each recipe's own screen. Display only:
 * nothing here feeds the planner, the budget, or any goal.
 */
@Composable
fun WeekNutritionCard(
    recipesById: Map<String, Recipe>,
    byItemId: Map<String, IngredientNutrition>,
    byPantrySlug: Map<String, IngredientNutrition>,
    weeks: List<NutritionWeekInput>,
    householdSize: Int,
    modifier: Modifier = Modifier,
    /** Scanned label facts by item id (v0.6.3): those ingredients read their labels, not USDA. */
    labelByItemId: Map<String, LabelNutrition> = emptyMap(),
) {
    Card(modifier = modifier.fillMaxWidth().testTag("week-nutrition-card")) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Week nutrition (estimate)", style = MaterialTheme.typography.titleLarge)
            // Provenance follows the computed weeks (Codex review, v0.6.3):
            // the USDA header prints only when USDA numbers actually fed a
            // total, and the label line only when a label did — a week fed
            // entirely by scanned labels never claims USDA estimates.
            val weekNutritions = remember(weeks, recipesById, householdSize, byItemId, byPantrySlug, labelByItemId) {
                weeks.map { week ->
                    WeekNutrition.forWeek(
                        week.nights, recipesById, householdSize, byItemId, byPantrySlug, labelByItemId,
                    )
                }
            }
            if (weekNutritions.any { it?.usdaUsed == true }) {
                Text(NUTRITION_ESTIMATE_LABEL, style = MaterialTheme.typography.bodySmall)
            }
            if (weekNutritions.any { it?.labelFactsUsed == true }) {
                Text(
                    "Ingredients whose products were scanned into the price book " +
                        "use their own label facts instead of the USDA entry.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (weeks.isEmpty()) {
                Text(
                    "Plan a week and its dinners' nutrition will add up here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            weeks.forEachIndexed { index, week ->
                val weekNutrition = weekNutritions[index]
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
            NutritionFormat.partialNote(nutrition.incompleteFields, nutrition.labelFactsUsed),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (nutrition.labelFactsUsed) {
        Text(
            "Includes nutrition from product labels, scanned from barcodes in the price book.",
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
