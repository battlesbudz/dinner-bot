package com.battlesbudz.dinnerbot.feature.premium

import android.app.Activity
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The Premium paywall (Phase C3), written by hand rather than rendered
 * by a paywall builder so every word on it is this app's own.
 *
 * Honesty rules it is built to:
 * - When the store supplied the products, prices are the store's own
 *   formatted prices for this user's storefront. Nothing is re-typed
 *   by hand, so the paywall can never quote a price the store would
 *   not charge.
 * - The annual plan's trial line appears only when the store data for
 *   that product carries a free trial phase (see [RevenueCatBilling]).
 * - When billing is not configured in this build, the screen says so
 *   plainly, shows the decided plan prices as a preview, and disables
 *   every purchase action instead of pretending to sell something.
 * - Nothing here threatens lost data or expiring access. There is no
 *   countdown, no fake scarcity, and closing the screen is one tap.
 */
@Composable
fun PaywallScreen(
    billing: PremiumBilling,
    isPremium: Boolean,
    onPremiumChanged: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var products by remember { mutableStateOf<List<PaywallProduct>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun loadProducts() {
        scope.launch {
            products = billing.loadProducts()
        }
    }

    LaunchedEffect(billing) {
        if (billing.isConfigured) loadProducts()
    }

    fun buy(product: PaywallProduct) {
        val activity = context as? Activity
        if (activity == null) {
            message = "Purchases need the app in front of you. Close anything covering it and try again."
            return
        }
        busy = true
        message = null
        scope.launch {
            when (val result = billing.purchase(activity, product)) {
                PurchaseResult.Purchased -> {
                    onPremiumChanged(true)
                    message = "Premium is on. Everything below is unlocked."
                }
                PurchaseResult.Cancelled -> message = null
                is PurchaseResult.Failed -> message = result.message
                PurchaseResult.Unavailable -> {
                    message = "Purchases are not available yet in this build."
                }
            }
            busy = false
        }
    }

    fun restore() {
        busy = true
        message = null
        scope.launch {
            when (val result = billing.restorePurchases()) {
                PurchaseResult.Purchased -> {
                    onPremiumChanged(true)
                    message = "Your earlier purchase is restored. Premium is on."
                }
                PurchaseResult.Cancelled -> message = null
                is PurchaseResult.Failed -> message = result.message
                PurchaseResult.Unavailable -> {
                    message = "Purchases are not available yet in this build."
                }
            }
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("paywall-screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Dinner Bot Premium", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Premium removes the caps and unlocks the extra planning tools. " +
                "The free app underneath stays the whole app: one-week planning, your " +
                "won't-eat list, the weekly check-in, backups, and typing or correcting " +
                "anything by hand are always free, with or without Premium.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (isPremium) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Premium is on", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unlimited imports and scans, two-week planning, diet presets, " +
                            "and your savings history are all unlocked on this device. " +
                            "Manage or cancel the subscription any time in Settings, " +
                            "under Premium.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        } else {
            Text("What Premium adds", style = MaterialTheme.typography.titleMedium)
            Text(
                "• Unlimited recipe imports (free: 2 a month)\n" +
                    "• Unlimited barcode scans (free: 15 to start, then 10 a month)\n" +
                    "• Two-week planning on one trip\n" +
                    "• Diet presets\n" +
                    "• Savings history and reports",
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                !billing.isConfigured -> {
                    FallbackPlanCards()
                    Text(
                        "Purchases are not available yet in this build of Dinner Bot, so " +
                            "the buttons are off. The prices above are the launch prices; " +
                            "when purchases open, the store will show its own prices here.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag("paywall-unavailable-note"),
                    )
                }
                products == null -> {
                    Text(
                        "Loading the store's prices…",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                products.isNullOrEmpty() -> {
                    FallbackPlanCards()
                    Text(
                        "The store's prices could not be loaded, so purchases are paused " +
                            "here. Check your connection and try again. The prices above " +
                            "are the launch prices.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(
                        onClick = { loadProducts() },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Try again") }
                }
                else -> {
                    products!!.forEach { product ->
                        ProductCard(
                            product = product,
                            busy = busy,
                            onBuy = { buy(product) },
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { restore() },
                enabled = billing.isConfigured && !busy,
                modifier = Modifier.fillMaxWidth().testTag("paywall-restore"),
            ) { Text("Restore purchases") }
        }

        message?.let { text ->
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("paywall-message"),
            )
        }

        TextButton(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().testTag("paywall-close"),
        ) { Text("Close") }
    }
}

/** One store product as a buyable card, priced by the store itself. */
@Composable
private fun ProductCard(
    product: PaywallProduct,
    busy: Boolean,
    onBuy: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(product.title, style = MaterialTheme.typography.titleMedium)
            Text(
                "${product.priceFormatted} ${product.periodLabel}",
                style = MaterialTheme.typography.headlineSmall,
            )
            product.trialLabel?.let { trial ->
                Text(trial, style = MaterialTheme.typography.bodyMedium)
            }
            if (product.kind == PaywallProductKind.LIFETIME) {
                Text(
                    "Pay once. Premium stays on, with no renewal.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(
                onClick = onBuy,
                enabled = !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(
                        when (product.kind) {
                            PaywallProductKind.MONTHLY -> "paywall-buy-monthly"
                            PaywallProductKind.ANNUAL -> "paywall-buy-annual"
                            PaywallProductKind.LIFETIME -> "paywall-buy-lifetime"
                        },
                    ),
            ) {
                Text(
                    when (product.kind) {
                        PaywallProductKind.MONTHLY -> "Choose monthly"
                        PaywallProductKind.ANNUAL -> "Choose annual"
                        PaywallProductKind.LIFETIME -> "Choose lifetime"
                    },
                )
            }
        }
    }
}

/**
 * The decided launch prices, shown only when the store cannot speak
 * for itself (billing unconfigured, or its prices failed to load). The
 * screen always labels the state it is in, so these static prices are
 * never mistaken for a live store quote.
 */
@Composable
private fun FallbackPlanCards() {
    listOf(
        Triple("Premium monthly", "$4.99 per month", null),
        Triple("Premium annual", "$39.99 per year", "14-day free trial, then $39.99/year"),
        Triple("Premium lifetime", "$79.99 one-time payment", "Pay once. Premium stays on, with no renewal."),
    ).forEach { (title, price, note) ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(price, style = MaterialTheme.typography.headlineSmall)
                note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}
