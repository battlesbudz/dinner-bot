package com.battlesbudz.dinnerbot.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PlannedNight
import com.battlesbudz.dinnerbot.core.model.Recipe
import com.battlesbudz.dinnerbot.core.model.RecipeRating
import java.io.File

internal val DAY_LABELS = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday",
    "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday",
)

/** The not-cooking reasons (Phase V2-2): stored key to shown label. */
internal val NOT_COOKING_REASONS = listOf(
    "leftovers" to "Leftovers",
    "eating out" to "Eating out",
    "other" to "Other",
)

internal fun notCookingReasonLabel(reason: String?): String =
    NOT_COOKING_REASONS.firstOrNull { it.first == reason }?.second ?: "Other"

/** The reason picker shown when a night is marked not cooking. */
@Composable
internal fun NotCookingReasonDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not cooking this night?") },
        text = {
            Text(
                "Its dinner, ingredients, and cost come off the plan and the list. " +
                    "You can put the night back any time.",
            )
        },
        confirmButton = {
            Column {
                NOT_COOKING_REASONS.forEach { (key, label) ->
                    TextButton(onClick = { onPick(key) }) { Text(label) }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * One night's card inside a week view: the dinner with swap and thumbs, or
 * the not-cooking state with its reason. Shared by the one-week screen and
 * the two-week screen (Phase V2-3) so a night behaves the same in both.
 */
@Composable
internal fun NightCard(
    night: PlannedNight,
    recipe: Recipe?,
    rating: RecipeRating?,
    onSwap: () -> Unit,
    onNotCookingRequest: () -> Unit,
    onCookThisNight: () -> Unit,
    onRate: (RecipeRating?) -> Unit,
    /** Open this night's cook view (Phase V2-7): check-offs and ran-out marks. */
    onCook: () -> Unit = {},
    /** The household's photo of this dinner, when one is attached (Epic issue #10). */
    photoFile: File? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (night.notCooking) {
                // Marked off for this week only: no dinner, no swap,
                // no thumbs. Its ingredients and cash are already out
                // of the plan and the list.
                Text(DAY_LABELS[night.day] ?: night.day, style = MaterialTheme.typography.titleSmall)
                Text(
                    "Not cooking: ${notCookingReasonLabel(night.notCookingReason)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Nothing is planned, priced, or listed for this night.",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = onCookThisNight) {
                    Text("Cook this night")
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (photoFile != null) {
                    RecipePhotoThumb(
                        file = photoFile,
                        contentDescription = "Photo of ${recipe?.title ?: night.recipeId}",
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .size(56.dp)
                            .clip(MaterialTheme.shapes.small),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(DAY_LABELS[night.day] ?: night.day, style = MaterialTheme.typography.titleSmall)
                    Text(recipe?.title ?: night.recipeId, style = MaterialTheme.typography.titleMedium)
                    if (recipe != null) {
                        Text(
                            "${recipe.mainProtein} · ${recipe.activeMinutes} min",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(onClick = onSwap) { Text("Swap") }
                    TextButton(onClick = onNotCookingRequest) { Text("Not cooking") }
                }
            }
            // The cook view (Phase V2-7): tonight's ingredients with
            // check-offs, and the ran-out marking Sunday's check consumes.
            TextButton(onClick = onCook) { Text("Start cooking") }
            // Rate this dinner after cooking it. Tapping the active
            // thumb again clears the rating.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { onRate(if (rating == RecipeRating.UP) null else RecipeRating.UP) },
                ) { Text(if (rating == RecipeRating.UP) "👍 Liked it ✓" else "👍 Liked it") }
                TextButton(
                    onClick = { onRate(if (rating == RecipeRating.DOWN) null else RecipeRating.DOWN) },
                ) { Text(if (rating == RecipeRating.DOWN) "👎 Not for us ✓" else "👎 Not for us") }
            }
        }
    }
}

/**
 * Week view: one card per cook night, the two headline numbers (cash needed
 * and attributed food cost, always separate), the Sam's Club savings line,
 * and the planner's plain-language notes.
 */
@Composable
fun PlannerScreen(
    plan: WeekPlanner.WeekPlan?,
    recipesById: Map<String, Recipe>,
    budgetCents: Int,
    onGenerate: () -> Unit,
    onSwap: (String) -> Unit,
    modifier: Modifier = Modifier,
    ratings: Map<String, RecipeRating> = emptyMap(),
    onRate: (String, RecipeRating?) -> Unit = { _, _ -> },
    outOfRotationCount: Int = 0,
    /** (day, cooking, reason) — mark a night not cooking (with a reason) or back to cooking. */
    onSetNightCooking: (String, Boolean, String?) -> Unit = { _, _, _ -> },
    /** Plan two weeks on one trip instead (Phase V2-3); null hides the button. */
    onGenerateTwoWeeks: (() -> Unit)? = null,
    /** Open one night's cook view (Phase V2-7), by night code. */
    onCookNight: (String) -> Unit = {},
    /** Household photo files by recipe id, for nights whose dinner has one (Epic issue #10). */
    photoFiles: Map<String, File> = emptyMap(),
) {
    // The day whose not-cooking reason is being picked, if any.
    var reasonForDay by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("This week's dinners", style = MaterialTheme.typography.headlineMedium)
        Button(onClick = onGenerate, modifier = Modifier.fillMaxWidth()) {
            Text(if (plan == null) "Plan my week" else "Regenerate week")
        }
        if (onGenerateTwoWeeks != null) {
            OutlinedButton(onClick = onGenerateTwoWeeks, modifier = Modifier.fillMaxWidth()) {
                Text("Plan two weeks on one trip")
            }
        }

        if (plan == null) {
            Text(
                "No plan yet. Dinner Bot picks dinners for your cook nights, prices them from " +
                    "the price book, and keeps the cash under your weekly budget.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Cash needed this week", style = MaterialTheme.typography.titleSmall)
                Text(Money(plan.cashNeededCents).format(), style = MaterialTheme.typography.headlineSmall)
                if (budgetCents > 0) {
                    Text(
                        "Weekly budget: ${Money(budgetCents).format()}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    "Food cost of these dinners: ${Money(plan.foodCostCents).format()} " +
                        "(what the meals themselves use up; bulk packs bought this week count in cash, not here)",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (plan.samsGateMet) {
                        "Sam's Club saves about ${Money(plan.samsMonthlySavingsCents).format()} this month"
                    } else {
                        "Sam's Club would save about ${Money(plan.samsMonthlySavingsCents).format()} this month, " +
                            "under the $15 mark, so bulk items are planned at regular stores this week"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        plan.nights.forEach { night ->
            NightCard(
                night = night,
                recipe = recipesById[night.recipeId],
                rating = ratings[night.recipeId],
                onSwap = { onSwap(night.day) },
                onNotCookingRequest = { reasonForDay = night.day },
                onCookThisNight = { onSetNightCooking(night.day, true, null) },
                onRate = { rating -> onRate(night.recipeId, rating) },
                onCook = { onCookNight(night.day) },
                photoFile = photoFiles[night.recipeId],
            )
        }

        plan.notes.forEach { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }

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

    val pickingDay = reasonForDay
    if (pickingDay != null) {
        NotCookingReasonDialog(
            onDismiss = { reasonForDay = null },
            onPick = { reason ->
                onSetNightCooking(pickingDay, false, reason)
                reasonForDay = null
            },
        )
    }
}
