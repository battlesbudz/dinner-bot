package com.battlesbudz.dinnerbot.feature.pantry

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.BulkStock
import com.battlesbudz.dinnerbot.core.model.PantryItem
import com.battlesbudz.dinnerbot.core.model.PriceItem

/**
 * Pantry: the owned/not-owned staple checklist the planner trusts, plus the
 * quantity-tracked Sam's Club bulk stock the planner draws down at $0 cash.
 */
@Composable
fun PantryScreen(
    pantryItems: List<PantryItem>,
    bulkPriceItems: List<PriceItem>,
    stocks: List<BulkStock>,
    onTogglePantry: (PantryItem) -> Unit,
    onAddStaple: (String) -> Unit,
    onStockChanged: (BulkStock) -> Unit,
    modifier: Modifier = Modifier,
) {
    var newStaple by remember { mutableStateOf("") }
    val stockByItem = remember(stocks) { stocks.associateBy { it.itemId } }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Pantry", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Ticked staples are treated as already home and never land on a shopping list.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        items(pantryItems, key = { it.id }) { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = item.owned, onCheckedChange = { onTogglePantry(item) })
                Text(item.name, style = MaterialTheme.typography.bodyLarge)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newStaple,
                    onValueChange = { newStaple = it },
                    label = { Text("Add a staple") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        if (newStaple.isNotBlank()) {
                            onAddStaple(newStaple.trim())
                            newStaple = ""
                        }
                    },
                ) { Text("Add") }
            }
        }
        item {
            Text(
                "Sam's Club bulk stock",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "What is portioned and sitting at home. The planner uses this first, at $0 cash, " +
                    "before it plans a new pack.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (bulkPriceItems.isEmpty()) {
            item { Text("No Sam's Club items in the price book yet.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(bulkPriceItems, key = { it.id }) { item ->
            val current = stockByItem[item.id]?.onHand
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "On hand: ${trimNumber(current?.amount ?: 0.0)} ${item.packQuantity.unit.label}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    TextButton(onClick = { onStockChanged(PantryLogic.adjustStock(item, current, -1)) }) {
                        Text("-")
                    }
                    TextButton(onClick = { onStockChanged(PantryLogic.adjustStock(item, current, +1)) }) {
                        Text("+")
                    }
                }
            }
        }
    }
}

private fun trimNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format("%.2f", value).trimEnd('0').trimEnd('.')
