package com.battlesbudz.dinnerbot.feature.grocerylist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.GroceryLine
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.Store

/** One persisted shopping row, as displayed. [rowId] is the Room row id. */
data class GroceryRow(
    val rowId: Long,
    val line: GroceryLine,
)

private fun storeLabel(store: Store): String = when (store) {
    Store.ALDI -> "Aldi"
    Store.WALMART -> "Walmart"
    Store.SAMS_CLUB -> "Sam's Club"
}

/**
 * Shopping mode: the list split into per-store sections with an estimated
 * cash subtotal per store, check-off boxes, and tap-a-price correction.
 * Correcting a price writes back to the price book (handled by the caller).
 */
@Composable
fun GroceryListScreen(
    rows: List<GroceryRow>,
    onToggleChecked: (GroceryRow, Boolean) -> Unit,
    onPriceCorrected: (GroceryRow, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<GroceryRow?>(null) }

    if (rows.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Text("Shopping list", style = MaterialTheme.typography.headlineMedium)
            Text(
                "No list yet. Plan a week first and the list builds itself from the dinners.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        return
    }

    val byStore = remember(rows) { rows.groupBy { it.line.store } }
    val total = rows.sumOf { GroceryListBuilder.lineCashCents(it.line) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Shopping list", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Estimated cash total: ${Money(total).format()}. Tap a price to fix it; " +
                    "your number is saved to the price book.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Store.entries.forEach { store ->
            val storeRows = byStore[store] ?: return@forEach
            item {
                val subtotal = storeRows.sumOf { GroceryListBuilder.lineCashCents(it.line) }
                Text(
                    "${storeLabel(store)} — about ${Money(subtotal).format()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(storeRows, key = { it.rowId }) { row ->
                val line = row.line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = line.checkedOff,
                        onCheckedChange = { checked -> onToggleChecked(row, checked) },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            line.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (line.checkedOff) TextDecoration.LineThrough else null,
                        )
                        Text(
                            "Need ${trimNumber(line.needed.amount)} ${line.needed.unit.label}" +
                                if (line.packsToBuy > 1) " · buy ${line.packsToBuy} packs" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        Money(GroceryListBuilder.lineCashCents(line)).format(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.clickable { editing = row }.padding(8.dp),
                    )
                }
            }
        }
    }

    editing?.let { row ->
        PriceEditDialog(
            title = row.line.displayName,
            currentTotalCents = GroceryListBuilder.lineCashCents(row.line),
            onDismiss = { editing = null },
            onSave = { cents ->
                onPriceCorrected(row, cents)
                editing = null
            },
        )
    }
}

@Composable
internal fun PriceEditDialog(
    title: String,
    currentTotalCents: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var text by remember { mutableStateOf((currentTotalCents / 100.0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What did this line actually cost? Your price is saved to the price book.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Price") },
                    prefix = { Text("$") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cents = PriceCorrection.parseToCents(text)
                    if (cents != null && cents > 0) onSave(cents)
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
