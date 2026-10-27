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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.PresetStores
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StoreIds

/** One plain-words explanation page, shared by first run and the Settings replay. */
data class ExplanationPage(val title: String, val body: String)

val EXPLANATION_PAGES: List<ExplanationPage> = listOf(
    ExplanationPage(
        "Welcome to Dinner Bot",
        "Dinner Bot plans a week of dinners for your household and turns it into " +
            "one shopping list. Everything stays on this phone: no account, no " +
            "server, nothing leaves the device.",
    ),
    ExplanationPage(
        "One cash budget for the week",
        "You set one weekly cash budget, and Dinner Bot keeps the week's shopping " +
            "under it across every store combined. A big bulk pack counts in " +
            "full the week you buy it, then costs $0 while the stock you have at " +
            "home lasts, so some weeks come out cheaper than others.",
    ),
    ExplanationPage(
        "Your stores, one list",
        "You pick the stores you shop at — the big chains, a regional favorite, " +
            "or your own local market — and the list splits by your stores so one " +
            "trip can cover them all. Prices start at national averages: the " +
            "first time you scan an item, set its correct local price, and the " +
            "app remembers it from then on. If a price at the store is different, " +
            "tap it on the list and your number becomes the price book's number.",
    ),
)

@Composable
private fun ExplanationPager(
    page: Int,
    onPageChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    lastPageNextLabel: String? = null,
    onLastPageNext: (() -> Unit)? = null,
) {
    val current = EXPLANATION_PAGES[page]
    val isLast = page == EXPLANATION_PAGES.lastIndex
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(current.title, style = MaterialTheme.typography.headlineMedium)
        Text(current.body, style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onPageChange(page - 1) }, enabled = page > 0) { Text("Back") }
            Text(
                "${page + 1} of ${EXPLANATION_PAGES.size}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (isLast && onLastPageNext != null) {
                Button(onClick = onLastPageNext) { Text(lastPageNextLabel ?: "Next") }
            } else {
                TextButton(onClick = { onPageChange(page + 1) }, enabled = !isLast) { Text("Next") }
            }
        }
    }
}

/** Settings replay: the same explanation pages, with a Done button to leave. */
@Composable
fun HowItWorksScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember { mutableStateOf(0) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ExplanationPager(page = page, onPageChange = { page = it })
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

private const val SETUP_STEP_COUNT = 7 // size, nights, budget, won't-eat, stores, staples, finish

/**
 * First-run setup: the explanation pages first, then the household setup
 * questions as steps in order (household size, cook nights, weekly cash
 * budget, won't-eat list, your stores, pantry staples), ending on a
 * "Generate my first week" button. The caller owns persistence: [onFinish] receives the
 * completed settings (onboardingComplete = true) and is expected to save
 * them, generate the first week, and land the user on the Plan tab.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupWalkthrough(
    initial: HouseholdSettings,
    onFinish: (HouseholdSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    var explaining by remember { mutableStateOf(true) }
    var explanationPage by remember { mutableStateOf(0) }
    var setupStep by remember { mutableStateOf(0) }

    var householdSize by remember { mutableStateOf(initial.householdSize) }
    var cookNights by remember { mutableStateOf(initial.cookNights.toSet()) }
    // The budget step follows the suggested budget (Phase C1) until the
    // user types their own figure.
    var budgetOverride by remember {
        mutableStateOf(
            if (initial.weeklyBudgetCents > 0 &&
                initial.weeklyBudgetCents !=
                OnboardingDefaults.suggestedBudgetCents(initial.householdSize, initial.cookNights.size)
            ) {
                (initial.weeklyBudgetCents / 100).toString()
            } else {
                null
            },
        )
    }
    val budgetText: String = budgetOverride
        ?: (OnboardingDefaults.suggestedBudgetCents(householdSize, cookNights.size) / 100).toString()
    var wontEatText by remember { mutableStateOf(initial.wontEat.joinToString(", ")) }
    var dietNotes by remember { mutableStateOf(initial.dietNotes) }
    var stores by remember { mutableStateOf(initial.stores) }
    var customStoreName by remember { mutableStateOf("") }
    var staples by remember { mutableStateOf(initial.pantryStaples.toSet()) }

    fun buildSettings(): HouseholdSettings = initial.copy(
        householdSize = householdSize,
        cookNights = OnboardingDefaults.NIGHTS.map { it.first }.filter { it in cookNights },
        weeklyBudgetCents = (budgetText.toIntOrNull() ?: 0) * 100,
        wontEat = wontEatText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        dietNotes = dietNotes.trim(),
        stores = stores,
        pantryStaples = staples.toList(),
        onboardingComplete = true,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (explaining) {
            ExplanationPager(
                page = explanationPage,
                onPageChange = { explanationPage = it },
                lastPageNextLabel = "Set up my household",
                onLastPageNext = { explaining = false },
            )
            return@Column
        }

        Text(
            "Setup, step ${setupStep + 1} of $SETUP_STEP_COUNT",
            style = MaterialTheme.typography.bodySmall,
        )
        when (setupStep) {
            0 -> {
                Text("How many are you feeding?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Recipes are scaled to this many servings when the week is planned.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (householdSize > 1) householdSize -= 1 }) { Text("-") }
                    Text(householdSize.toString(), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { householdSize += 1 }) { Text("+") }
                }
            }
            1 -> {
                Text("Which nights do you cook?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Dinner Bot plans one dinner for each night you pick.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OnboardingDefaults.NIGHTS.forEach { (code, label) ->
                        FilterChip(
                            selected = code in cookNights,
                            onClick = {
                                cookNights = if (code in cookNights) cookNights - code else cookNights + code
                            },
                            label = { Text(label) },
                        )
                    }
                }
            }
            2 -> {
                Text("Weekly dinner budget", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pre-filled with a suggestion for your household size and cook nights — " +
                        "change it to whatever fits. The budget caps the cash spent each " +
                        "week across all your stores combined.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { budgetOverride = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Dollars per week") },
                    prefix = { Text("$") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            3 -> {
                Text("Anything you won't eat?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "If a dinner can be made without one of these, the ingredient simply comes " +
                        "out. If a dinner cannot be made without it, that dinner is left out of " +
                        "generated weeks. These are planning filters only, not medical or " +
                        "allergen guarantees.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
            }
            4 -> {
                Text("Your stores", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Pick the stores you shop at, or add your own by name. Shopping " +
                        "lists group by these stores, and the first one picked is the " +
                        "default when a store is needed.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PresetStores.ALL.forEach { preset ->
                        FilterChip(
                            selected = stores.any { it.id == preset.id },
                            onClick = {
                                stores = if (stores.any { it.id == preset.id }) {
                                    stores.filterNot { it.id == preset.id }
                                } else {
                                    stores + preset
                                }
                            },
                            label = { Text(preset.name) },
                        )
                    }
                }
                stores.filter { store -> PresetStores.ALL.none { it.id == store.id } }.forEach { store ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(store.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { stores = stores - store }) { Text("Remove") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customStoreName,
                        onValueChange = { customStoreName = it },
                        label = { Text("Add your own store") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = {
                            val name = customStoreName.trim()
                            if (name.isNotEmpty()) {
                                stores = stores + StoreIds.customStore(
                                    name,
                                    stores.map { it.id } + PresetStores.ALL.map { it.id },
                                )
                                customStoreName = ""
                            }
                        },
                    ) { Text("Add") }
                }
            }
            5 -> {
                Text("Pantry staples you already have", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Ticked staples are treated as already owned and are never put on a shopping list.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OnboardingDefaults.DEFAULT_STAPLES.forEach { staple ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = staple in staples,
                            onCheckedChange = { checked ->
                                staples = if (checked) staples + staple else staples - staple
                            },
                        )
                        Text(staple)
                    }
                }
            }
            else -> {
                Text("You are all set", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Dinner Bot will build your first week now: dinners for your cook nights, " +
                        "priced from the price book, cash under your budget, and a shopping " +
                        "list split by store.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = { onFinish(buildSettings()) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Generate my first week") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            OutlinedButton(onClick = {
                if (setupStep == 0) {
                    explaining = true
                    explanationPage = EXPLANATION_PAGES.lastIndex
                } else {
                    setupStep -= 1
                }
            }) { Text("Back") }
            if (setupStep < SETUP_STEP_COUNT - 1) {
                Button(onClick = { setupStep += 1 }) { Text("Next") }
            }
        }
    }
}
