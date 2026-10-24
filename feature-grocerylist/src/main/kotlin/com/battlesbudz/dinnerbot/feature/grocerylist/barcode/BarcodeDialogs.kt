package com.battlesbudz.dinnerbot.feature.grocerylist.barcode

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection
import com.battlesbudz.dinnerbot.feature.pricebook.storeDisplayName

/**
 * A scanned code resolved to [item]: show the price currently held and
 * take the shelf price Andrea is looking at. [headerText] and
 * [initialCents] carry whichever context applies: the book's pack price
 * for a general scan, or the scanned line's total when the scan came
 * from that very item's shopping line (the caller then runs that line's
 * correction, store and all). Confirming hands the cents back; the
 * caller writes them through the price book's correction paths, so a
 * scan correction and a typed correction land identically. [onRelink]
 * offers a way out when the link points at the wrong item.
 */
@Composable
fun ScanPriceDialog(
    item: PriceItem,
    headerText: String,
    initialCents: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    onRelink: (() -> Unit)? = null,
) {
    var text by remember(item.id, initialCents) { mutableStateOf((initialCents / 100.0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("scan-price-dialog"),
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    headerText,
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
 * belongs to, adds the product as a NEW price book item when the book
 * does not have it (PR v0.4.4; [onAddNew] gets the lookup's name hint,
 * or null, to prefill the form), or skips and carries on with
 * tap-to-correct. While the picker is open, the optional Open Food
 * Facts lookup may offer the product's name as a hint (see
 * [OpenFoodFactsLookup]); it appears only when a connection produced
 * one, and picking stays hers either way.
 */
@Composable
fun LinkBarcodeDialog(
    barcode: String,
    candidates: List<PriceItem>,
    onPick: (PriceItem) -> Unit,
    onAddNew: (suggestedName: String?) -> Unit,
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
                            "pick the item it matches, or add it as a new item.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAddNew(suggestion) }
                        .testTag("link-add-new-item")
                        .padding(vertical = 8.dp),
                ) {
                    Text(
                        "None of these — add it as a new item",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "For a product the price book does not have yet. You name it, " +
                            "pick its store and pack, and type the shelf price.",
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

/** A tiny single-choice picker used by the new-item form's tap-to-change rows. */
@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onPick(index) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * The add-a-new-item form (PR v0.4.4): a scanned product the price book
 * does not have yet becomes a book item right here, in the aisle. The
 * name is prefilled from the Open Food Facts hint when there is one and
 * stays editable; the home store starts at the store the scan belongs
 * to; the pack and the shelf price are typed from the package in hand.
 * Confirming builds the item through [BarcodeScanLogic.buildUserItem]
 * and hands it to the caller, which saves it and links the barcode.
 */
@Composable
fun NewItemFormDialog(
    barcode: String,
    suggestedName: String?,
    initialStore: Store,
    categories: List<String>,
    today: String,
    existingIds: Set<String>,
    onConfirm: (PriceItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(barcode) { mutableStateOf(suggestedName ?: "") }
    var category by remember(barcode) {
        mutableStateOf(categories.firstOrNull { it == "Other" } ?: categories.firstOrNull() ?: "Other")
    }
    var store by remember(barcode) { mutableStateOf(initialStore) }
    var packDescription by remember(barcode) { mutableStateOf("") }
    var packAmountText by remember(barcode) { mutableStateOf("") }
    var packUnit by remember(barcode) { mutableStateOf(MeasureUnit.COUNT) }
    var priceText by remember(barcode) { mutableStateOf("") }
    var picker by remember(barcode) { mutableStateOf<String?>(null) }

    val priceCents = PriceCorrection.parseToCents(priceText)
    val packAmount = packAmountText.toDoubleOrNull()
    val canSave = name.isNotBlank() && priceCents != null && priceCents > 0 &&
        packAmount != null && packAmount > 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("new-item-dialog"),
        title = { Text("Add a new price book item") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    "Barcode $barcode is a product the price book does not have. " +
                        "Fill this in from the package in your hand; the item is saved " +
                        "to the book and the barcode is linked to it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new-item-name"),
                )
                TextButton(
                    onClick = { picker = "store" },
                    modifier = Modifier.testTag("new-item-store"),
                ) { Text("Home store: ${storeDisplayName(store)} — tap to change") }
                TextButton(
                    onClick = { picker = "category" },
                    modifier = Modifier.testTag("new-item-category"),
                ) { Text("Category: $category — tap to change") }
                OutlinedTextField(
                    value = packDescription,
                    onValueChange = { packDescription = it },
                    label = { Text("Pack (e.g. Bag, 4 lb)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new-item-pack-description"),
                )
                OutlinedTextField(
                    value = packAmountText,
                    onValueChange = { packAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Pack amount") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new-item-pack-amount"),
                )
                TextButton(
                    onClick = { picker = "unit" },
                    modifier = Modifier.testTag("new-item-unit"),
                ) { Text("Pack unit: ${packUnit.label} — tap to change") }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Shelf price") },
                    prefix = { Text("$") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new-item-price"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                modifier = Modifier.testTag("new-item-confirm"),
                onClick = {
                    val item = runCatching {
                        BarcodeScanLogic.buildUserItem(
                            name = name,
                            category = category,
                            homeStore = store,
                            packDescription = packDescription.ifBlank {
                                "${packAmountText.trim()} ${packUnit.label}"
                            },
                            packQuantity = Quantity(packAmount!!, packUnit),
                            shelfPriceCents = priceCents!!,
                            checkedDate = today,
                            existingIds = existingIds,
                        )
                    }.getOrNull()
                    if (item != null) onConfirm(item)
                },
            ) { Text("Save to the price book") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picker) {
        "store" -> OptionPickerDialog(
            title = "Home store",
            options = Store.entries.map { storeDisplayName(it) },
            onPick = { index -> store = Store.entries[index]; picker = null },
            onDismiss = { picker = null },
        )
        "category" -> OptionPickerDialog(
            title = "Category",
            options = categories,
            onPick = { index -> category = categories[index]; picker = null },
            onDismiss = { picker = null },
        )
        "unit" -> OptionPickerDialog(
            title = "Pack unit",
            options = MeasureUnit.entries.map { it.label },
            onPick = { index -> packUnit = MeasureUnit.entries[index]; picker = null },
            onDismiss = { picker = null },
        )
    }
}

/**
 * A scan from a shopping line that named a DIFFERENT item than the
 * line's (PR v0.4.4, Battles's drumsticks case: the line buys skinless
 * chicken breasts, the package in hand is drumsticks). She can buy this
 * instead — onto THIS week's list at the typed per-pack price, as many
 * packs as the dinner takes, the planned line off this week only, the
 * planned item's book price and the recipe untouched — or just save
 * the scanned item's price, the behavior scans already had. When a
 * past swap is remembered for this line's item, [remembered] names it:
 * a suggestion, shown and never applied by itself.
 */
@Composable
fun SubstituteChoiceDialog(
    scannedItem: PriceItem,
    lineDisplayName: String,
    initialCents: Int,
    remembered: ScanSubstitution?,
    rememberedSubstituteName: String?,
    onSubstitute: (Int) -> Unit,
    onSavePriceOnly: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(scannedItem.id, initialCents) { mutableStateOf((initialCents / 100.0).toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("substitute-choice-dialog"),
        title = { Text(scannedItem.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "This is not the $lineDisplayName on your list. Buy this instead, " +
                        "at the shelf price for the pack, and the $lineDisplayName comes " +
                        "off this week's list only — its price in the book and the recipe " +
                        "stay as they are.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (remembered != null && rememberedSubstituteName != null) {
                    Text(
                        "Last time you swapped in $rememberedSubstituteName for this.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("substitute-remembered-hint"),
                    )
                }
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
                modifier = Modifier.testTag("substitute-buy-instead"),
                onClick = {
                    val cents = PriceCorrection.parseToCents(text)
                    if (cents != null && cents > 0) onSubstitute(cents)
                },
            ) { Text("Buy this instead this week") }
        },
        dismissButton = {
            Column {
                TextButton(
                    onClick = {
                        val cents = PriceCorrection.parseToCents(text)
                        if (cents != null && cents > 0) onSavePriceOnly(cents)
                    },
                ) { Text("Just save the price") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
