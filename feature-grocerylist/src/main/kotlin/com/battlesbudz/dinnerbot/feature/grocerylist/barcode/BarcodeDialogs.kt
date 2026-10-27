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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.Money
import com.battlesbudz.dinnerbot.core.model.NewBookItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.ScanSubstitution
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.barcode.OffProduct
import com.battlesbudz.dinnerbot.feature.barcode.OpenFoodFactsLookup
import com.battlesbudz.dinnerbot.feature.pricebook.AddPriceItemDialog
import com.battlesbudz.dinnerbot.feature.pricebook.PriceCorrection

/**
 * A scanned code resolved to [item]: show the price currently held and
 * take the shelf price the user is looking at. [headerText] and
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
 * A scanned code with no link yet: the user picks the price book item it
 * belongs to, adds the product as a NEW price book item when the book
 * does not have it (PR v0.4.4; [onAddNew] gets the lookup's name hint,
 * or null, to prefill the form), or skips and carries on with
 * tap-to-correct. While the picker is open, the optional Open Food
 * Facts lookup may offer the product's name as a hint (see
 * [OpenFoodFactsLookup]); it appears only when a connection produced
 * one, and picking stays hers either way. Since v0.6.3 the lookup's
 * label facts ride along: picking an item also captures the facts
 * onto it through [onLabelFacts].
 */
@Composable
fun LinkBarcodeDialog(
    barcode: String,
    candidates: List<PriceItem>,
    onPick: (PriceItem) -> Unit,
    onAddNew: (suggestedName: String?) -> Unit,
    onSkip: () -> Unit,
    /** Label facts the lookup returned, captured onto the item she picks. */
    onLabelFacts: (itemId: String, facts: LabelNutrition) -> Unit = { _, _ -> },
    /** The lookup's product, reported so a following form reuses it instead of fetching again. */
    onProductKnown: (OffProduct?) -> Unit = {},
) {
    var product by remember(barcode) { mutableStateOf<OffProduct?>(null) }
    val suggestion = product?.name
    LaunchedEffect(barcode) {
        val found = OpenFoodFactsLookup.lookupProduct(barcode)
        product = found
        onProductKnown(found)
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
                        "For a product the price book does not have yet. You name it " +
                            "and pick its store and pack; the shelf price can be typed " +
                            "now or left blank to be guessed.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(candidates, key = { it.id }) { item ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    product
                                        ?.labelNutrition(
                                            item.id, barcode, System.currentTimeMillis(),
                                        )
                                        ?.let { facts -> onLabelFacts(item.id, facts) }
                                    onPick(item)
                                }
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

/**
 * The add-a-new-item form (PR v0.4.4), now the shared add-an-ingredient
 * form (v0.6.2): a scanned product the price book does not have yet
 * becomes a book item right here, in the aisle, through the same form
 * the recipe editor and the price book use, so the record is identical
 * wherever it was added. The name is prefilled from the Open Food
 * Facts hint when there is one and stays editable; the home store
 * starts at the store the scan belongs to; the pack is typed from the
 * package in hand. The shelf price is OPTIONAL now: left blank, the
 * item is saved guess-priced and marked as a guess (its barcode still
 * links), and the first real price clears the mark — a missing price
 * no longer blocks adding the product. The caller saves the item and
 * links the barcode.
 */
@Composable
fun NewItemFormDialog(
    barcode: String,
    suggestedName: String?,
    initialStore: String,
    today: String,
    book: List<PriceItem>,
    onConfirm: (PriceItem) -> Unit,
    onDismiss: () -> Unit,
    /** Label facts captured for the saved item from this barcode's lookup (v0.6.3). */
    onLabelFacts: (itemId: String, facts: LabelNutrition) -> Unit = { _, _ -> },
    /** The product the link picker's lookup already returned for this barcode, when it did. */
    initialProduct: OffProduct? = null,
) {
    AddPriceItemDialog(
        book = book,
        today = today,
        initialName = suggestedName ?: "",
        initialStore = initialStore,
        title = "Add a new price book item",
        intro = "Barcode $barcode is a product the price book does not have. " +
            "Fill this in from the package in your hand; the item is saved " +
            "to the book and the barcode is linked to it.",
        confirmLabel = "Save to the price book",
        pricedSource = NewBookItem.SCAN_PRICED_SOURCE,
        guessSource = NewBookItem.SCAN_GUESS_SOURCE,
        // The barcode was scanned to get here, so the form opens with
        // it: the lookup prefills the pack and captures the label
        // facts, and no second scan button is offered.
        showScanButton = false,
        initialBarcode = barcode,
        initialProduct = initialProduct,
        onLabelFacts = onLabelFacts,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/**
 * A scan from a shopping line that named a DIFFERENT item than the
 * line's (PR v0.4.4, the user's drumsticks case: the line buys skinless
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
