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
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * The Sunday reconciliation screens (Phase V2-7, scope B4 step 1): three
 * steps in the user's exact order, shown before a new week is planned.
 * Step 1: "These are the things you indicated you ran out of." Step 2:
 * "Did you run out of these also?" Step 3, full checks only: the pantry
 * she owns, checking off what is actually missing. The lighter top-up
 * check runs steps 1 and 2 only. "Reconcile later" is on every step:
 * skipping leaves all state untouched and the ran-out list waits at the
 * top of the next attempt.
 */
@Composable
fun ReconciliationScreen(
    session: Reconciliation.Session,
    onComplete: (Reconciliation.SessionChoices) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember(session) { mutableStateOf(0) }
    var confirmed by remember(session) { mutableStateOf<Set<String>>(emptySet()) }
    var dismissed by remember(session) { mutableStateOf<Set<String>>(emptySet()) }
    var missingPantry by remember(session) { mutableStateOf<Set<String>>(emptySet()) }
    var missingBulk by remember(session) { mutableStateOf<Set<String>>(emptySet()) }
    val lastStep = session.stepCount - 1

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (session.kind == Reconciliation.CheckKind.FULL) {
                "Before the new week: a quick check"
            } else {
                "Before week 2's top-up shop: a quick check"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            "Step ${step + 1} of ${session.stepCount}",
            style = MaterialTheme.typography.bodyMedium,
        )

        when (step) {
            0 -> {
                Text(
                    "These are the things you indicated you ran out of",
                    style = MaterialTheme.typography.titleLarge,
                )
                if (session.markedOuts.isEmpty()) {
                    Text(
                        "Nothing was marked ran out while cooking.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                session.markedOuts.forEach { event ->
                    Card(modifier = Modifier.fillMaxWidth().testTag("recon-marked-${event.itemKey}")) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(event.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Marked ran out on ${event.dateMarked}. It joins this week's list " +
                                    "by the usual rules.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
            1 -> {
                Text("Did you run out of these also?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Last week's cooked dinners used these, and nothing bought them back. " +
                        "Tap \"Ran out\" for the ones that are gone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (session.possibleOuts.isEmpty()) {
                    Text(
                        "Nothing else looks used up.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                session.possibleOuts.forEach { possible ->
                    Card(modifier = Modifier.fillMaxWidth().testTag("recon-possible-${possible.itemKey}")) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(possible.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(possible.reason, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (possible.itemKey in confirmed) {
                                    Button(onClick = {
                                        confirmed = confirmed - possible.itemKey
                                    }) { Text("Ran out ✓") }
                                } else {
                                    OutlinedButton(onClick = {
                                        confirmed = confirmed + possible.itemKey
                                        dismissed = dismissed - possible.itemKey
                                    }) { Text("Ran out") }
                                }
                                if (possible.itemKey in dismissed) {
                                    Button(onClick = {
                                        dismissed = dismissed - possible.itemKey
                                    }) { Text("Still have it ✓") }
                                } else {
                                    OutlinedButton(onClick = {
                                        dismissed = dismissed + possible.itemKey
                                        confirmed = confirmed - possible.itemKey
                                    }) { Text("Still have it") }
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                Text("Your pantry", style = MaterialTheme.typography.titleLarge)
                Text(
                    "This is what Dinner Bot believes you have. Check off anything you are " +
                        "actually missing; it joins the ran-out list.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                session.pantryChecklist.forEach { staple ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().testTag("recon-pantry-${staple.id}"),
                    ) {
                        Checkbox(
                            checked = staple.id in missingPantry,
                            onCheckedChange = { checked ->
                                missingPantry =
                                    if (checked) missingPantry + staple.id else missingPantry - staple.id
                            },
                        )
                        Text(staple.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (session.bulkChecklist.isNotEmpty()) {
                    Text("Bulk stock", style = MaterialTheme.typography.titleLarge)
                    session.bulkChecklist.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().testTag("recon-bulk-${item.id}"),
                        ) {
                            Checkbox(
                                checked = item.id in missingBulk,
                                onCheckedChange = { checked ->
                                    missingBulk =
                                        if (checked) missingBulk + item.id else missingBulk - item.id
                                },
                            )
                            Text("${item.name} (all gone)", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onSkip) { Text("Reconcile later") }
            if (step > 0) {
                OutlinedButton(onClick = { step -= 1 }) { Text("Back") }
            }
            if (step < lastStep) {
                Button(onClick = { step += 1 }, modifier = Modifier.weight(1f)) { Text("Next") }
            } else {
                Button(
                    onClick = {
                        onComplete(
                            Reconciliation.SessionChoices(
                                confirmedPossibleKeys = confirmed,
                                dismissedPossibleKeys = dismissed,
                                missingPantryIds = missingPantry,
                                missingBulkItemIds = missingBulk,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        if (session.kind == Reconciliation.CheckKind.FULL) {
                            "Finish and plan the week"
                        } else {
                            "Finish and update the top-up list"
                        },
                    )
                }
            }
        }
    }
}
