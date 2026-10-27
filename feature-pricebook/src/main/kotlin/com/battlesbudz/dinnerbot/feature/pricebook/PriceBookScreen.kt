package com.battlesbudz.dinnerbot.feature.pricebook

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.PresetStores
import com.battlesbudz.dinnerbot.core.model.SeedPrices
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.core.model.StoreIds

/** Display name for a store id, resolved against the user's stores (Phase C1). */
fun storeDisplayName(storeId: String, stores: List<Store> = emptyList()): String =
    StoreIds.displayName(storeId, stores)

/**
 * The price book: every priced item with its store and pack price. Tap a
 * price to correct it, tap the store to move the item's home store. New
 * ingredients are added with the shared add-an-ingredient form
 * ([AddPriceItemDialog]); a price left blank starts the item at a
 * guessed price marked as a guess, never blocking the add (v0.6.2).
 */
@Composable
fun PriceBookScreen(
    priceItems: List<PriceItem>,
    onItemEdited: (PriceItem) -> Unit,
    modifier: Modifier = Modifier,
    today: String = java.time.LocalDate.now().toString(),
    onItemAdded: (PriceItem) -> Unit = {},
    /** Resolves a barcode scanned in the add form to the item it already belongs to (v0.6.3). */
    existingItemForBarcode: ((String) -> PriceItem?)? = null,
    /** A barcode scanned in the add form, to link to the item it created. */
    onBarcodeLinked: (barcode: String, itemId: String) -> Unit = { _, _ -> },
    /** Label facts captured by a scan in the add form. */
    onLabelFacts: (itemId: String, facts: LabelNutrition) -> Unit = { _, _ -> },
    /** The user's stores, for store names and the home-store picker (Phase C1). */
    stores: List<Store> = emptyList(),
) {
    var editingPrice by remember { mutableStateOf<PriceItem?>(null) }
    var editingStore by remember { mutableStateOf<PriceItem?>(null) }
    var addingItem by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Price book", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Estimates Dinner Bot plans with. Tap a price when the store charges " +
                    "something different; your number wins from then on.",
                style = MaterialTheme.typography.bodyMedium,
            )
            // Phase C1 disclosure: seed prices are national averages
            // until the user corrects them locally.
            Text(
                SeedPrices.DISCLOSURE,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp).testTag("seed-price-disclosure"),
            )
            Button(
                onClick = { addingItem = true },
                modifier = Modifier.padding(top = 8.dp).testTag("pricebook-add"),
            ) { Text("Add an ingredient") }
        }
        items(priceItems, key = { it.id }) { item ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${item.packDescription} · ${item.category}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            storeDisplayName(item.homeStore, stores),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable { editingStore = item }.padding(4.dp),
                        )
                        Text(
                            Money(item.packPriceCents).format(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.clickable { editingPrice = item }.padding(4.dp),
                        )
                    }
                    if (item.guessPriced) {
                        Text(
                            "Guessed price — not shopped yet",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (item.userCorrected) {
                        Text("Your corrected price", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    editingPrice?.let { item ->
        var text by remember(item.id) { mutableStateOf((item.packPriceCents / 100.0).toString()) }
        AlertDialog(
            onDismissRequest = { editingPrice = null },
            title = { Text(item.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pack price for ${item.packDescription}")
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
                TextButton(onClick = {
                    val cents = PriceCorrection.parseToCents(text)
                    if (cents != null && cents > 0) {
                        onItemEdited(PriceCorrection.applyPackPrice(item, cents))
                        editingPrice = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editingPrice = null }) { Text("Cancel") } },
        )
    }

    editingStore?.let { item ->
        AlertDialog(
            onDismissRequest = { editingStore = null },
            title = { Text("Home store for ${item.name}") },
            text = {
                Column {
                    stores.ifEmpty { PresetStores.ALL }.forEach { store ->
                        DropdownMenuItem(
                            text = { Text(store.name) },
                            onClick = {
                                onItemEdited(PriceCorrection.applyHomeStore(item, store.id))
                                editingStore = null
                            },
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { editingStore = null }) { Text("Cancel") } },
        )
    }

    if (addingItem) {
        AddPriceItemDialog(
            book = priceItems,
            today = today,
            onConfirm = { item ->
                onItemAdded(item)
                addingItem = false
            },
            onDismiss = { addingItem = false },
            existingItemForBarcode = existingItemForBarcode,
            stores = stores,
            onExistingItemScanned = { addingItem = false },
            onBarcodeLinked = onBarcodeLinked,
            onLabelFacts = onLabelFacts,
        )
    }
}
