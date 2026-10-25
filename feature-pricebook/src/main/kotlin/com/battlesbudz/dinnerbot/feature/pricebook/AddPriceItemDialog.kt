package com.battlesbudz.dinnerbot.feature.pricebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NewBookItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RecipePricing
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.Store

/**
 * The one add-an-ingredient form (v0.6.2 hotfix), shared by every door
 * that puts a new item in the price book: the ingredient editor in
 * recipe creation, editing, the cook view, and import review; the
 * price book's own add button; and a barcode scan of a product the
 * book does not have. Every field and every outcome is the same
 * wherever Andrea meets it.
 *
 * The price is OPTIONAL — that is the fix. Leaving it blank creates
 * the item anyway at a guessed price marked as a guess
 * ([NewBookItem] runs the guess machinery), so an ingredient she
 * cannot price on the spot can never dead-end the flow she is in.
 * Typing a price makes it her number, user-corrected, no guess mark.
 *
 * [pricedSource]/[guessSource] name the door on the saved item, the
 * way scans have always recorded "Added by barcode scan".
 */
@Composable
fun AddPriceItemDialog(
    book: List<PriceItem>,
    today: String,
    onConfirm: (PriceItem) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    initialStore: Store = Store.ALDI,
    title: String = "Add an ingredient",
    intro: String = "Anything not in the price book yet can start here. " +
        "The price can be left blank — Dinner Bot will guess it and mark it as a guess " +
        "until the item is shopped.",
    confirmLabel: String = "Add to the price book",
    pricedSource: String = NewBookItem.HAND_PRICED_SOURCE,
    guessSource: String = NewBookItem.HAND_GUESS_SOURCE,
) {
    val categories = remember(book) {
        (book.map { it.category }.distinct().sorted() + "Other").distinct()
    }
    var name by remember { mutableStateOf(initialName) }
    var store by remember { mutableStateOf(initialStore) }
    var pickedCategory by remember { mutableStateOf<String?>(null) }
    // The category the form will save: Andrea's own pick once she
    // makes one. Before that, the category of a book item this name
    // is equivalent to — a new kind of onion lands in Produce, not
    // Other — so perishability and the fresh top-up trip treat the
    // item right from the start; with no equivalent, the standing
    // default. (NewBookItem only inherits an equivalent's category
    // from a BLANK category, so the default has to be resolved here.)
    val suggestedCategory = remember(name, book) {
        RecipePricing.equivalentsFor(name.trim(), book).firstOrNull()?.category
    }
    val category = pickedCategory ?: suggestedCategory
        ?: if ("Other" in categories) "Other" else categories.firstOrNull() ?: "Other"
    var packDescription by remember { mutableStateOf("") }
    var packAmountText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(MeasureUnit.COUNT) }
    var priceText by remember { mutableStateOf("") }
    // Which option picker is showing: "store", "category", "unit", or none.
    var picker by remember { mutableStateOf<String?>(null) }

    val packAmount = packAmountText.toDoubleOrNull()
    val priceCents = if (priceText.isBlank()) null else PriceCorrection.parseToCents(priceText)
    val canSave = name.isNotBlank() &&
        packAmount != null && packAmount > 0.0 &&
        (priceText.isBlank() || (priceCents != null && priceCents > 0))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(intro, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add-item-name"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { picker = "store" },
                        modifier = Modifier.testTag("add-item-store"),
                    ) { Text("Store: ${storeDisplayName(store)}") }
                    TextButton(
                        onClick = { picker = "category" },
                        modifier = Modifier.testTag("add-item-category"),
                    ) { Text("Category: $category") }
                }
                OutlinedTextField(
                    value = packDescription,
                    onValueChange = { packDescription = it },
                    label = { Text("Pack description (e.g. Bottle, 16 oz)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add-item-pack-description"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packAmountText,
                        onValueChange = { packAmountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Pack amount") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("add-item-pack-amount"),
                    )
                    TextButton(
                        onClick = { picker = "unit" },
                        modifier = Modifier.testTag("add-item-unit"),
                    ) { Text(unit.label) }
                }
                OutlinedTextField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Price (optional)") },
                    prefix = { Text("$") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("add-item-price"),
                )
                Text(
                    "No price? Leave it blank — the item is added at a guessed price, " +
                        "marked as a guess until it is shopped or corrected.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onConfirm(
                        NewBookItem.build(
                            name = name,
                            category = category,
                            homeStore = store,
                            packDescription = packDescription,
                            packQuantity = Quantity(packAmount!!, unit),
                            priceCents = priceCents,
                            book = book,
                            checkedDate = today,
                            pricedSource = pricedSource,
                            guessSource = guessSource,
                        ),
                    )
                },
                modifier = Modifier.testTag("add-item-confirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    when (picker) {
        "store" -> AddItemOptionPicker(
            title = "Home store",
            options = Store.entries.map { storeDisplayName(it) to it.name },
            onPick = { picked ->
                store = Store.valueOf(picked)
                picker = null
            },
            onDismiss = { picker = null },
        )
        "category" -> AddItemOptionPicker(
            title = "Category",
            options = categories.map { it to it },
            onPick = { picked ->
                pickedCategory = picked
                picker = null
            },
            onDismiss = { picker = null },
        )
        "unit" -> AddItemOptionPicker(
            title = "Pack unit",
            options = MeasureUnit.entries.map { it.label to it.name },
            onPick = { picked ->
                unit = MeasureUnit.valueOf(picked)
                picker = null
            },
            onDismiss = { picker = null },
        )
    }
}

/** A plain choose-one list dialog used by the add-item form's pickers. */
@Composable
private fun AddItemOptionPicker(
    title: String,
    options: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = { onPick(value) },
                        modifier = Modifier.testTag("add-item-picker-$value"),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
