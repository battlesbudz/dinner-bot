package com.battlesbudz.dinnerbot.feature.premium

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PremiumFeature
import com.battlesbudz.dinnerbot.core.model.SavingsReport

/**
 * The words for every premium gate (Phase C3). Each prompt names what
 * the free tier gives, when it refreshes, what Premium changes, and
 * the free path that stays open — adding by hand, one-week planning,
 * the standing won't-eat list. No prompt anywhere in the app suggests
 * data is at risk without Premium, because none is.
 */
object GateCopy {

    fun title(feature: PremiumFeature): String = when (feature) {
        PremiumFeature.RECIPE_IMPORT -> "This month's free imports are used up"
        PremiumFeature.BARCODE_SCAN -> "Your free scans are used up"
        PremiumFeature.TWO_WEEK_PLANNING -> "Two-week planning is part of Premium"
        PremiumFeature.DIET_PRESETS -> "Diet presets are part of Premium"
        PremiumFeature.SAVINGS_HISTORY -> "Savings history is part of Premium"
    }

    fun body(feature: PremiumFeature): String = when (feature) {
        PremiumFeature.RECIPE_IMPORT ->
            "Free Dinner Bot includes 2 recipe imports a month, and they come back on " +
                "the 1st. Premium imports without a limit. Adding a recipe by typing it " +
                "in yourself stays free either way."
        PremiumFeature.BARCODE_SCAN ->
            "Premium scans barcodes without a limit. Typing a price in by hand always " +
                "works and stays free: tap any price on the list to correct it, or fill " +
                "the form in without scanning."
        PremiumFeature.TWO_WEEK_PLANNING ->
            "Premium plans two weeks on one trip: the big shop plus the fresh top-up. " +
                "One-week planning stays free."
        PremiumFeature.DIET_PRESETS ->
            "Presets apply while Premium is on. Picks you have already saved stay saved " +
                "and apply again the moment Premium returns. Your own won't-eat list " +
                "stays free and always applies."
        PremiumFeature.SAVINGS_HISTORY ->
            "Premium keeps your savings history: what you saved this month, your " +
                "under-budget streaks, and the running total. The weekly check-in " +
                "itself stays free."
    }
}

/**
 * The contextual prompt shown at a gate: the user just tried the thing,
 * so the prompt explains that exact limit and offers the paywall or a
 * one-tap dismissal that sticks (the caller returns to the free path).
 */
@Composable
fun PremiumGateDialog(
    feature: PremiumFeature,
    onSeePremium: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("premium-gate-dialog"),
        title = { Text(GateCopy.title(feature)) },
        text = { Text(GateCopy.body(feature)) },
        confirmButton = {
            Button(
                onClick = onSeePremium,
                modifier = Modifier.testTag("premium-gate-see-premium"),
            ) { Text("See Premium plans") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("premium-gate-dismiss"),
            ) { Text("Not now") }
        },
    )
}

/**
 * The periodic in-app Premium reminder (Phase C3): a plain card on the
 * Plan tab, never a push notification and never a pop-up. Whether it
 * shows at all is decided by PremiumRules.shouldShowPeriodicPrompt —
 * never during onboarding, never before a first plan, and a dismissal
 * here suppresses it for the documented interval.
 */
@Composable
fun PremiumPromptCard(
    onSeePremium: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag("premium-prompt-card")) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Dinner Bot Premium", style = MaterialTheme.typography.titleMedium)
            Text(
                "Unlimited imports and scans, two-week planning, diet presets, and " +
                    "your savings history. The free app keeps everything it has now.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onSeePremium,
                modifier = Modifier.fillMaxWidth().testTag("premium-prompt-see-premium"),
            ) { Text("See Premium plans") }
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().testTag("premium-prompt-dismiss"),
            ) { Text("Not now") }
        }
    }
}

/**
 * The savings history section (Phase C3): the premium half of the
 * weekly money story. Free users see what the section is and where
 * the plans live; the free rollover and check-in are untouched by this
 * gate and live where they always have. Premium users see the report
 * computed by SavingsHistory in core-model.
 */
@Composable
fun SavingsHistorySection(
    report: SavingsReport?,
    isPremium: Boolean,
    onSeePremium: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth().testTag("savings-history-section")) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Savings history", style = MaterialTheme.typography.titleMedium)
            if (!isPremium) {
                Text(
                    "What you have saved this month, your under-budget streaks, and " +
                        "the running total live here with Premium.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(
                    onClick = onSeePremium,
                    modifier = Modifier.fillMaxWidth().testTag("savings-history-see-premium"),
                ) { Text("See Premium plans") }
            } else if (report == null || report.weeksPlanned == 0) {
                Text(
                    "No weeks planned yet. Your history starts with your first plan " +
                        "and builds from there.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Saved this month: ${Money(report.savedThisMonthCents).format()}",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Saved all told: ${Money(report.totalSavedCents).format()}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val streak = report.currentUnderBudgetStreakWeeks
                val streakText = if (streak > 0) {
                    " — currently $streak ${if (streak == 1) "week" else "weeks"} in a row."
                } else {
                    "."
                }
                Text(
                    "Under budget ${report.weeksUnderBudget} of ${report.weeksPlanned} " +
                        "planned weeks$streakText",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

/**
 * The Premium section of Settings (Phase C3): quiet, factual, always
 * reachable. Shows the current status, opens the paywall, manages the
 * subscription through Google Play's own subscription page (the
 * store account owns the subscription, so that is where cancel and
 * payment changes live), and restores purchases.
 */
@Composable
fun PremiumSettingsSection(
    isPremium: Boolean,
    billingConfigured: Boolean,
    onSeePremium: () -> Unit,
    onRestore: () -> Unit,
    restoreBusy: Boolean,
    restoreMessage: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier.fillMaxWidth().testTag("premium-settings-section"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Premium", style = MaterialTheme.typography.titleMedium)
        Text(
            if (isPremium) {
                "Premium is on for this device. Unlimited imports and scans, two-week " +
                    "planning, diet presets, and savings history are unlocked."
            } else {
                "You are on the free plan. Premium adds unlimited imports and scans, " +
                    "two-week planning, diet presets, and savings history. Everything " +
                    "free stays free."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!billingConfigured) {
            Text(
                "Purchases are not available yet in this build of Dinner Bot.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!isPremium) {
            Button(
                onClick = onSeePremium,
                modifier = Modifier.fillMaxWidth().testTag("premium-settings-see-premium"),
            ) { Text("See Premium plans") }
        } else {
            OutlinedButton(
                onClick = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            "https://play.google.com/store/account/subscriptions" +
                                "?package=com.battlesbudz.dinnerbot.app",
                        ),
                    )
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth().testTag("premium-settings-manage"),
            ) { Text("Manage subscription in Google Play") }
        }
        OutlinedButton(
            onClick = onRestore,
            enabled = billingConfigured && !restoreBusy,
            modifier = Modifier.fillMaxWidth().testTag("premium-settings-restore"),
        ) { Text("Restore purchases") }
        restoreMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("premium-settings-restore-message"),
            )
        }
    }
}
