package com.battlesbudz.dinnerbot.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.HouseholdRules
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings

object OnboardingDefaults {
    val NIGHTS: List<Pair<String, String>> = listOf(
        "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu",
        "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun",
    )

    val DEFAULT_STAPLES: List<String> = listOf(
        "Vegetable oil", "Butter", "Salt", "Black pepper", "Garlic powder",
        "Onion powder", "Chili powder", "Ground cumin", "Paprika", "Soy sauce",
        "Long grain white rice", "Flour", "Sugar",
    )

    /** First-run starting point: household of 4, weekday cooking, all staples pre-ticked. */
    fun initialSettings(): HouseholdSettings = HouseholdSettings(
        householdSize = 4,
        cookNights = listOf("MON", "TUE", "WED", "THU", "FRI"),
        weeklyBudgetCents = 0,
        pantryStaples = DEFAULT_STAPLES,
        onboardingComplete = false,
    )
}

/**
 * First-run and settings form. State is hoisted: the caller owns persistence
 * and receives the finished [HouseholdSettings] via [onSave].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingForm(
    initial: HouseholdSettings,
    onSave: (HouseholdSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var householdSize by remember { mutableStateOf(initial.householdSize) }
    var cookNights by remember { mutableStateOf(initial.cookNights.toSet()) }
    var budgetText by remember {
        mutableStateOf(if (initial.weeklyBudgetCents > 0) (initial.weeklyBudgetCents / 100).toString() else "")
    }
    var wontEatText by remember { mutableStateOf(initial.wontEat.joinToString(", ")) }
    var dietNotes by remember { mutableStateOf(initial.dietNotes) }
    var staples by remember { mutableStateOf(initial.pantryStaples.toSet()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Welcome to Dinner Bot", style = MaterialTheme.typography.headlineMedium)
        Text(
            "A few things about your household, once. Everything stays on this phone. " +
                "No account, no server, nothing leaves the device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Text("How many are you feeding?", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { if (householdSize > 1) householdSize -= 1 }) { Text("-") }
            Text(householdSize.toString(), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { householdSize += 1 }) { Text("+") }
        }

        Text("Which nights do you cook?", style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OnboardingDefaults.NIGHTS.forEach { (code, label) ->
                FilterChip(
                    selected = code in cookNights,
                    onClick = { cookNights = if (code in cookNights) cookNights - code else cookNights + code },
                    label = { Text(label) },
                )
            }
        }

        Text("Weekly dinner budget (cash, across all stores)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = budgetText,
            onValueChange = { budgetText = it.filter { ch -> ch.isDigit() } },
            label = { Text("Dollars per week") },
            prefix = { Text("$") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "The budget caps the cash spent each week at Aldi, Walmart, and Sam's Club combined.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Anything you won't eat?", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = wontEatText,
            onValueChange = { wontEatText = it },
            label = { Text("Separate items with commas") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = dietNotes,
            onValueChange = { dietNotes = it },
            label = { Text("Diet notes (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "These are planning filters only, not medical or allergen guarantees.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Household substitution rule", style = MaterialTheme.typography.titleSmall)
                Text(HouseholdRules.GROUND_MEAT_SUBSTITUTION, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text("Pantry staples you already have", style = MaterialTheme.typography.titleMedium)
        Text(
            "Ticked staples are treated as already owned and are never put on a shopping list.",
            style = MaterialTheme.typography.bodySmall,
        )
        OnboardingDefaults.DEFAULT_STAPLES.forEach { staple ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = staple in staples,
                    onCheckedChange = { checked -> staples = if (checked) staples + staple else staples - staple },
                )
                Text(staple)
            }
        }

        Button(
            onClick = {
                onSave(
                    initial.copy(
                        householdSize = householdSize,
                        cookNights = OnboardingDefaults.NIGHTS.map { it.first }.filter { it in cookNights },
                        weeklyBudgetCents = (budgetText.toIntOrNull() ?: 0) * 100,
                        wontEat = wontEatText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        dietNotes = dietNotes.trim(),
                        pantryStaples = staples.toList(),
                        onboardingComplete = true,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save and see recipes")
        }
    }
}

/** Settings is the same form, reused after first run. */
@Composable
fun SettingsScreen(
    initial: HouseholdSettings,
    onSave: (HouseholdSettings) -> Unit,
    modifier: Modifier = Modifier,
) = OnboardingForm(initial = initial, onSave = onSave, modifier = modifier)
