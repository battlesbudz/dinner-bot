package com.battlesbudz.dinnerbot.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import java.io.File

/**
 * Two-week view (Phase V2-3): both weeks of a fortnight, each with its own
 * cash figure against the weekly budget, and the one-trip total above
 * them. Nights behave exactly as they do in the one-week view (swap,
 * thumbs, not cooking) through the shared [NightCard]; the callbacks
 * carry the week index (1 or 2) so a swap never pulls a dinner from the
 * other week.
 */
@Composable
fun FortnightPlannerScreen(
    plan: WeekPlanner.FortnightPlan,
    recipesById: Map<String, Recipe>,
    budgetCents: Int,
    onRegenerateTwoWeeks: () -> Unit,
    onPlanOneWeek: () -> Unit,
    onSwap: (Int, String) -> Unit,
    onSetNightCooking: (Int, String, Boolean, String?) -> Unit,
    modifier: Modifier = Modifier,
    ratings: Map<String, RecipeRating> = emptyMap(),
    onRate: (String, RecipeRating?) -> Unit = { _, _ -> },
    outOfRotationCount: Int = 0,
    /** Open one night's cook view (Phase V2-7): (weekIndex, day). */
    onCookNight: (Int, String) -> Unit = { _, _ -> },
    /**
     * The lighter Sunday check is due before week 2's top-up shop
     * (Phase V2-7); non-null shows its invitation. Null hides it.
     */
    onStartTopUpCheck: (() -> Unit)? = null,
    /** Household photo files by recipe id, for nights whose dinner has one (Epic issue #10). */
    photoFiles: Map<String, File> = emptyMap(),
    /** Recipe ids whose cost is not accurately reflected yet (Phase RI-3); see PlannerScreen. */
    unpricedRecipeIds: Set<String> = emptySet(),
    /** Estimated cost per serving by recipe id, for the night cards (v0.6.0 redesign). */
    perServingCentsByRecipeId: Map<String, Int> = emptyMap(),
    /** The weeks' nutrition summary card, composed by the caller (v0.6.0 redesign). */
    weekNutrition: @Composable () -> Unit = {},
    /** Extra sections under the plan, composed by the caller (Phase C3); see PlannerScreen. */
    belowPlanSection: @Composable () -> Unit = {},
) {
    // (weekIndex, day) whose not-cooking reason is being picked, if any.
    var reasonFor by remember { mutableStateOf<Pair<Int, String>?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Two weeks, one trip", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onRegenerateTwoWeeks, modifier = Modifier.fillMaxWidth()) {
            Text("Regenerate two weeks")
        }
        TextButton(onClick = onPlanOneWeek) {
            Text("Plan one week instead")
        }
        if (onStartTopUpCheck != null) {
            OutlinedButton(onClick = onStartTopUpCheck, modifier = Modifier.fillMaxWidth()) {
                Text("Quick check before week 2's top-up shop")
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cash for the whole trip", style = MaterialTheme.typography.titleSmall)
                Text(Money(plan.tripCashCents).format(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Week 1: ${Money(plan.week1.cashNeededCents).format()} · " +
                        "Week 2: ${Money(plan.week2.cashNeededCents).format()}" +
                        if (budgetCents > 0) " · weekly budget ${Money(budgetCents).format()} each week" else "",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (plan.topUpLines.isNotEmpty()) {
                    val topUpCash = plan.topUpLines.sumOf { it.packsToBuy * it.estimatedPackPriceCents }
                    Text(
                        "Of that, ${Money(topUpCash).format()} is week 2's fresh top-up, bought at " +
                            "the start of week 2 so nothing spoils. The big trip itself is " +
                            "${Money(plan.tripCashCents - topUpCash).format()}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                // Phase C1: each week's cash against the weekly budget.
                if (budgetCents > 0) {
                    Text(
                        "Week 1 cash ${Money(plan.week1.cashNeededCents).format()}, week 2 cash " +
                            "${Money(plan.week2.cashNeededCents).format()}, each against your " +
                            "${Money(budgetCents).format()} weekly budget.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        plan.notes.forEach { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

        WeekSection(
            title = "Week 1 · starts ${plan.week1.weekStart}",
            week = plan.week1,
            weekIndex = 1,
            recipesById = recipesById,
            ratings = ratings,
            onSwap = onSwap,
            onNotCookingRequest = { day -> reasonFor = 1 to day },
            onSetNightCooking = onSetNightCooking,
            onRate = onRate,
            onCookNight = onCookNight,
            photoFiles = photoFiles,
            unpricedRecipeIds = unpricedRecipeIds,
            perServingCentsByRecipeId = perServingCentsByRecipeId,
        )
        WeekSection(
            title = "Week 2 · starts ${plan.week2.weekStart}",
            week = plan.week2,
            weekIndex = 2,
            recipesById = recipesById,
            ratings = ratings,
            onSwap = onSwap,
            onNotCookingRequest = { day -> reasonFor = 2 to day },
            onSetNightCooking = onSetNightCooking,
            onRate = onRate,
            onCookNight = onCookNight,
            photoFiles = photoFiles,
            unpricedRecipeIds = unpricedRecipeIds,
            perServingCentsByRecipeId = perServingCentsByRecipeId,
        )

        weekNutrition()

        belowPlanSection()

        if (outOfRotationCount > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "$outOfRotationCount ${if (outOfRotationCount == 1) "dinner is" else "dinners are"} " +
                        "thumbed down and out of rotation. They stay in the recipe list; " +
                        "clear the thumbs down there to bring one back.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    val picking = reasonFor
    if (picking != null) {
        NotCookingReasonDialog(
            onDismiss = { reasonFor = null },
            onPick = { reason ->
                onSetNightCooking(picking.first, picking.second, false, reason)
                reasonFor = null
            },
        )
    }
}

@Composable
private fun WeekSection(
    title: String,
    week: WeekPlanner.WeekPlan,
    weekIndex: Int,
    recipesById: Map<String, Recipe>,
    ratings: Map<String, RecipeRating>,
    onSwap: (Int, String) -> Unit,
    onNotCookingRequest: (String) -> Unit,
    onSetNightCooking: (Int, String, Boolean, String?) -> Unit,
    onRate: (String, RecipeRating?) -> Unit,
    onCookNight: (Int, String) -> Unit,
    photoFiles: Map<String, File> = emptyMap(),
    unpricedRecipeIds: Set<String> = emptySet(),
    perServingCentsByRecipeId: Map<String, Int> = emptyMap(),
) {
    Text(title, style = MaterialTheme.typography.titleLarge)
    Text(
        "Cash needed this week: ${Money(week.cashNeededCents).format()} · " +
            "food cost of these dinners: ${Money(week.foodCostCents).format()}",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (week.nights.any { it.recipeId in unpricedRecipeIds }) {
        Text(
            "Includes guesses for a dinner whose ingredients are not all priced yet.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
    week.nights.forEach { night ->
        NightCard(
            night = night,
            recipe = recipesById[night.recipeId],
            rating = ratings[night.recipeId],
            onSwap = { onSwap(weekIndex, night.day) },
            onNotCookingRequest = { onNotCookingRequest(night.day) },
            onCookThisNight = { onSetNightCooking(weekIndex, night.day, true, null) },
            onRate = { rating -> onRate(night.recipeId, rating) },
            onCook = { onCookNight(weekIndex, night.day) },
            photoFile = photoFiles[night.recipeId],
            perServingCents = perServingCentsByRecipeId[night.recipeId],
            costNote = if (night.recipeId in unpricedRecipeIds) {
                "Some ingredients have no price yet, so this dinner's cost is " +
                    "a guess for now — it will be exact after the first shop."
            } else {
                null
            },
        )
    }
    week.notes.forEach { note ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
