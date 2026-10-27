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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.DietPreset
import com.battlesbudz.dinnerbot.core.model.PresetStores
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StoreIds
import com.battlesbudz.dinnerbot.core.model.HouseholdSettings
import com.battlesbudz.dinnerbot.core.model.ReminderLogic
import com.battlesbudz.dinnerbot.core.model.ReminderSettings
import com.battlesbudz.dinnerbot.core.model.ReminderTimes
import java.time.LocalTime

object OnboardingDefaults {
    val NIGHTS: List<Pair<String, String>> = listOf(
        "MON" to "Mon", "TUE" to "Tue", "WED" to "Wed", "THU" to "Thu",
        "FRI" to "Fri", "SAT" to "Sat", "SUN" to "Sun",
    )

    /**
     * The generic staples list the pantry step OFFERS (Phase C1). It is a
     * catalogue of common staples, not a household's pantry: nothing on
     * it is pre-ticked, in onboarding or anywhere else.
     */
    val DEFAULT_STAPLES: List<String> = listOf(
        "Vegetable oil", "Butter", "Salt", "Black pepper", "Garlic powder",
        "Onion powder", "Chili powder", "Ground cumin", "Paprika", "Soy sauce",
        "Long grain white rice", "Flour", "Sugar",
    )

    /**
     * The budget suggestion heuristic (Phase C1): $3.50 per person per
     * cook night covers a home-cooked dinner's ingredients at national
     * average prices, rounded to the nearest $5 so the prefilled figure
     * reads like a budget, not a calculation.
     */
    const val SUGGESTED_CENTS_PER_PERSON_PER_NIGHT: Int = 350

    /** The suggested weekly budget for a household, in cents. */
    fun suggestedBudgetCents(householdSize: Int, cookNights: Int): Int {
        val raw = householdSize.coerceAtLeast(0) * cookNights.coerceAtLeast(0) *
            SUGGESTED_CENTS_PER_PERSON_PER_NIGHT
        return ((raw + 250) / 500) * 500
    }

    /**
     * First-run starting point: household of 4, weekday cooking, the
     * suggested budget prefilled (editable in the form), and NO staples
     * pre-ticked.
     */
    fun initialSettings(): HouseholdSettings = HouseholdSettings(
        householdSize = 4,
        cookNights = listOf("MON", "TUE", "WED", "THU", "FRI"),
        weeklyBudgetCents = suggestedBudgetCents(4, 5),
        pantryStaples = emptyList(),
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
    /** The diet preset definitions the household can switch on (Phase V2-2). */
    dietPresets: List<DietPreset> = emptyList(),
    /**
     * Whether notifications can appear right now (Phase V2-4): true on
     * Android versions with no notification permission, or when the
     * permission is granted. The caller owns the permission state.
     */
    notificationsGranted: Boolean = true,
    /**
     * Called at the moment a reminder is switched on while notifications
     * are not granted, so the caller can ask for the permission then and
     * never earlier.
     */
    onRequestNotificationPermission: () -> Unit = {},
    /**
     * Diet presets are part of Premium (Phase C3): while locked, the
     * preset checkboxes show the picks as they stand and tapping one
     * calls [onDietPresetLockedTap] (the caller shows the Premium
     * prompt) instead of changing the pick. Picks already saved are
     * never removed by the lock; they apply again with Premium. The
     * first-run walkthrough leaves this unlocked: nothing is gated
     * during onboarding.
     */
    dietPresetsLocked: Boolean = false,
    onDietPresetLockedTap: () -> Unit = {},
) {
    var householdSize by remember { mutableStateOf(initial.householdSize) }
    var cookNights by remember { mutableStateOf(initial.cookNights.toSet()) }
    // The budget field follows the suggested budget (Phase C1) until
    // the user types their own figure; a saved budget that differs
    // from the suggestion starts as the user's own figure.
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
    var stores by remember { mutableStateOf(initial.stores) }
    var customStoreName by remember { mutableStateOf("") }
    var wontEatText by remember { mutableStateOf(initial.wontEat.joinToString(", ")) }
    var dietNotes by remember { mutableStateOf(initial.dietNotes) }
    var selectedPresets by remember { mutableStateOf(initial.dietPresets.toSet()) }
    var staples by remember { mutableStateOf(initial.pantryStaples.toSet()) }
    var reminders by remember { mutableStateOf(initial.reminders) }

    // Switching a reminder on is the one moment the permission is asked
    // for: never at install, never at first run (Phase V2-4).
    fun toggleReminder(on: Boolean, apply: (Boolean) -> ReminderSettings) {
        if (on && !notificationsGranted) onRequestNotificationPermission()
        reminders = apply(on)
    }

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
            onValueChange = { budgetOverride = it.filter { ch -> ch.isDigit() } },
            label = { Text("Dollars per week") },
            prefix = { Text("$") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Pre-filled with a suggestion for your household size and cook nights — " +
                "change it to whatever fits. The budget caps the cash spent each week " +
                "across all your stores combined.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("Your stores", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick the stores you shop at, or add your own. Lists group by these " +
                "stores, and the first one is the default when a store is needed.",
            style = MaterialTheme.typography.bodySmall,
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

        if (dietPresets.isNotEmpty()) {
            Text("Diet presets (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Named bundles that sit on top of your own list: each preset adds won't-eat " +
                    "entries and recipe filters of its own. A preset never removes anything you " +
                    "listed yourself. " +
                    "Like the list, presets are planning filters only, not medical or allergen guarantees.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (dietPresetsLocked) {
                Text(
                    "Diet presets are part of Premium. While Premium is off, saved picks " +
                        "stay saved and apply again when Premium is on; tap a preset to " +
                        "see the plans.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            dietPresets.forEach { preset ->
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = preset.id in selectedPresets,
                        onCheckedChange = { checked ->
                            if (dietPresetsLocked) {
                                onDietPresetLockedTap()
                            } else {
                                selectedPresets = if (checked) {
                                    selectedPresets + preset.id
                                } else {
                                    selectedPresets - preset.id
                                }
                            }
                        },
                    )
                    Column {
                        Text(preset.name, style = MaterialTheme.typography.bodyLarge)
                        Text(preset.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Reminders", style = MaterialTheme.typography.titleMedium)
        Text(
            "Reminders are scheduled on this phone only. Nothing leaves the device. " +
                "Dinner Bot asks for the notification permission only when you switch " +
                "a reminder on, and everything else works the same if you say no.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (ReminderLogic.warningNeeded(reminders, notificationsGranted)) {
            Text(
                "Notifications are turned off for Dinner Bot, so these reminders " +
                    "will not appear. To get them, allow notifications for Dinner Bot " +
                    "in Android's settings (Apps, Dinner Bot, Notifications).",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("reminders-permission-warning"),
            )
        }

        ReminderRow(
            title = "Dinner reminder",
            detail = "On each cook night, names tonight's dinner from your current plan.",
            checked = reminders.dinnerEnabled,
            switchTag = "reminder-dinner-switch",
            onCheckedChange = { on ->
                toggleReminder(on) { reminders.copy(dinnerEnabled = it) }
            },
        )
        if (reminders.dinnerEnabled) {
            ReminderTimeRow(
                time = reminders.dinnerTime,
                fallback = LocalTime.of(16, 30),
                onChange = { reminders = reminders.copy(dinnerTime = it) },
            )
        }

        ReminderRow(
            title = "Shopping reminder",
            detail = "On your shopping day, so the trip is not forgotten.",
            checked = reminders.shoppingEnabled,
            switchTag = "reminder-shopping-switch",
            onCheckedChange = { on ->
                toggleReminder(on) { reminders.copy(shoppingEnabled = it) }
            },
        )
        if (reminders.shoppingEnabled) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OnboardingDefaults.NIGHTS.forEach { (code, label) ->
                    FilterChip(
                        selected = reminders.shoppingDay == code,
                        onClick = { reminders = reminders.copy(shoppingDay = code) },
                        label = { Text(label) },
                    )
                }
            }
            ReminderTimeRow(
                time = reminders.shoppingTime,
                fallback = LocalTime.of(9, 0),
                onChange = { reminders = reminders.copy(shoppingTime = it) },
            )
        }

        ReminderRow(
            title = "Defrost reminder",
            detail = "The evening before a dinner that uses frozen bulk stock, " +
                "naming what to move to the fridge.",
            checked = reminders.defrostEnabled,
            switchTag = "reminder-defrost-switch",
            onCheckedChange = { on ->
                toggleReminder(on) { reminders.copy(defrostEnabled = it) }
            },
        )
        if (reminders.defrostEnabled) {
            ReminderTimeRow(
                time = reminders.defrostTime,
                fallback = LocalTime.of(20, 0),
                onChange = { reminders = reminders.copy(defrostTime = it) },
            )
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
                        stores = stores,
                        wontEat = wontEatText.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                        dietPresets = dietPresets.map { it.id }.filter { it in selectedPresets },
                        dietNotes = dietNotes.trim(),
                        pantryStaples = staples.toList(),
                        onboardingComplete = true,
                        reminders = reminders,
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
    dietPresets: List<DietPreset> = emptyList(),
    notificationsGranted: Boolean = true,
    onRequestNotificationPermission: () -> Unit = {},
    /** Diet presets are part of Premium (Phase C3); see [OnboardingForm]. */
    dietPresetsLocked: Boolean = false,
    onDietPresetLockedTap: () -> Unit = {},
) = OnboardingForm(
    initial = initial,
    onSave = onSave,
    modifier = modifier,
    dietPresets = dietPresets,
    notificationsGranted = notificationsGranted,
    onRequestNotificationPermission = onRequestNotificationPermission,
    dietPresetsLocked = dietPresetsLocked,
    onDietPresetLockedTap = onDietPresetLockedTap,
)

/** One reminder's switch row in the settings form's reminders block. */
@Composable
private fun ReminderRow(
    title: String,
    detail: String,
    checked: Boolean,
    switchTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTag),
        )
    }
}

/** A reminder's time, stepped in 15-minute moves to match the form's other steppers. */
@Composable
private fun ReminderTimeRow(
    time: String,
    fallback: LocalTime,
    onChange: (String) -> Unit,
) {
    val parsed = ReminderTimes.parseOrDefault(time, fallback)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Time", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange(ReminderTimes.format(parsed.minusMinutes(15))) }) {
            Text("-15 min")
        }
        Text(ReminderTimes.display(parsed), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange(ReminderTimes.format(parsed.plusMinutes(15))) }) {
            Text("+15 min")
        }
    }
}
