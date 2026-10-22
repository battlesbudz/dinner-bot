package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection

/**
 * A scanned code resolved to [item]: show the price the book currently
 * holds and take the shelf price Andrea is looking at. Confirming hands
 * the cents back; the caller writes them through the price book's
 * correction path, so a scan correction and a typed correction land
 * identically. [onRelink] offers a way out when the link points at the
 * wrong item.
 */
@Composable
fun ScanPriceDialog(
    item: PriceItem,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRelink: (() -> Unit)? = null,
) {
    var text by remember(item.id) { mutableStateOf((item.packPriceCents / 100.0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("scan-price-dialog"),
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The price book says ${Money(item.packPriceCents).format()} for " +
                        "${item.packDescription}. Type the shelf price for the pack; " +
                        "your number is saved to the price book.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Shelf price") },
                    prefix = { Text("$") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = PriceCorrection.parseToCents(text)
                    if (cents != null && cents > 0) onConfirm(cents)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Column {
                if (onRelink != null) {
                    TextButton(onClick = onRelink) { Text("Wrong item? Link a different one") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/**
 * A scanned code with no link yet: Andrea picks the price book item it
 * belongs to, or skips and carries on with tap-to-correct. While the
 * picker is open, the optional Open Food Facts lookup may offer the
 * product's name as a hint (see [OpenFoodFactsLookup]); it appears only
 * when a connection produced one, and picking stays hers either way.
 */
@Composable
fun LinkBarcodeDialog(
    barcode: String,
    candidates: List<PriceItem>,
    onPick: (PriceItem) -> Unit,
    onSkip: () -> Unit,
) {
    var suggestion by remember(barcode) { mutableStateOf<String?>(null) }
    LaunchedEffect(barcode) {
        suggestion = OpenFoodFactsLookup.suggestProductName(barcode)
    }
    AlertDialog(
        onDismissRequest = onSkip,
        modifier = Modifier.testTag("link-barcode-dialog"),
        title = { Text("First time seeing this barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Barcode $barcode is not linked to anything yet. Which price book " +
                        "item is it? Dinner Bot remembers your pick on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                suggestion?.let { name ->
                    Text(
                        "Open Food Facts calls this \"$name\". That is a hint only; " +
                            "pick the item it matches.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(item) }
                                .padding(vertical = 8.dp),
                        ) {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${item.packDescription} · ${Money(item.packPriceCents).format()}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
    )
}
