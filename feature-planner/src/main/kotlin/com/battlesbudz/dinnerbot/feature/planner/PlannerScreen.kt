package com.battlesbudz.dinnerbot.feature.planner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.Recipe

private val DAY_LABELS = mapOf(
    "MON" to "Monday", "TUE" to "Tuesday", "WED" to "Wednesday", "THU" to "Thursday",
    "FRI" to "Friday", "SAT" to "Saturday", "SUN" to "Sunday",
)

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
) {
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
            val recipe = recipesById[night.recipeId]
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    TextButton(onClick = { onSwap(night.day) }) { Text("Swap") }
                }
            }
        }

        plan.notes.forEach { note ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(note, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
