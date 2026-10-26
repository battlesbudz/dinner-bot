package com.battlesbudz.dinnerbot.feature.nutrition

import com.battlesbudz.dinnerbot.core.model.IngredientNutrition
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.NutritionCalculator
import com.battlesbudz.dinnerbot.core.model.NutritionField
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeNutrition
import kotlin.math.roundToInt

/** One planned week as the Nutrition tab reads it: a label and its nights. */
data class NutritionWeekInput(
    val label: String,
    val nights: List<PlannedNight>,
)

/**
 * The week's nutrition (Phase V2-6): every cooked night's recipe computed
 * at the household's servings, summed. The sum's totals are the per-night
 * figures added across the week and its per-serving figures are each
 * night's per-serving figures added across the week, which is how the tab
 * shows them. Nights marked not cooking and nights whose recipe is gone
 * contribute nothing. Pure Kotlin over core-model math: display only,
 * never an input to planning.
 */
object WeekNutrition {
    fun forWeek(
        nights: List<PlannedNight>,
        recipesById: Map<String, Recipe>,
        householdSize: Int,
        byItemId: Map<String, IngredientNutrition>,
        byPantrySlug: Map<String, IngredientNutrition>,
        labelByItemId: Map<String, LabelNutrition> = emptyMap(),
    ): RecipeNutrition? {
        val parts = nights.filterNot { it.notCooking }.mapNotNull { night ->
            recipesById[night.recipeId]?.let { recipe ->
                NutritionCalculator.recipeNutrition(
                    recipe, householdSize, byItemId, byPantrySlug, labelByItemId,
                )
            }
        }
        return if (parts.isEmpty()) null else NutritionCalculator.sum(parts)
    }
}

/** Display formatting for nutrition figures: whole numbers for kcal and mg, one decimal for grams. */
object NutritionFormat {
    fun amount(value: Double?, field: NutritionField): String {
        if (value == null) return "no data"
        return when (field.unitLabel) {
            "g" -> "${trimDecimal(value)} g"
            "mg" -> "${value.roundToInt()} mg"
            else -> "${value.roundToInt()} kcal"
        }
    }

    /**
     * The honest partial-data wording, naming the fields with no
     * sourced value. When a scanned label contributed, the gap may be
     * the label's rather than USDA's, so the wording stays neutral
     * about which source is missing the field.
     */
    fun partialNote(
        incompleteFields: Set<NutritionField>,
        labelFactsUsed: Boolean = false,
    ): String {
        val names = incompleteFields.sortedBy { it.ordinal }.joinToString(", ") { it.label.lowercase() }
        return if (labelFactsUsed) {
            "Partial data: no $names value for one or more ingredients here, " +
                "so the $names total is incomplete."
        } else {
            "Partial data: USDA FoodData Central has no $names value for one or more " +
                "ingredients here, so the $names total is incomplete."
        }
    }

    private fun trimDecimal(value: Double): String {
        val rounded = kotlin.math.round(value * 10) / 10.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            String.format("%.1f", rounded)
        }
    }
}
