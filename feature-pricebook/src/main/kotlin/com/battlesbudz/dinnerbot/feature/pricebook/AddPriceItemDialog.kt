package com.battlesbudz.dinnerbot.feature.pricebook

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.battlesbudz.dinnerbot.core.model.LabelNutrition
import com.battlesbudz.dinnerbot.core.model.MeasureUnit
import com.battlesbudz.dinnerbot.core.model.NewBookItem
import com.battlesbudz.dinnerbot.core.model.PriceItem
import com.battlesbudz.dinnerbot.core.model.RecipePricing
import com.battlesbudz.dinnerbot.core.model.Quantity
import com.battlesbudz.dinnerbot.core.model.PresetStores
import com.battlesbudz.dinnerbot.core.model.Store
import com.battlesbudz.dinnerbot.feature.barcode.BarcodeScannerDialog
import com.battlesbudz.dinnerbot.feature.barcode.LocalScanGate
import com.battlesbudz.dinnerbot.feature.barcode.OffPackParser
import com.battlesbudz.dinnerbot.feature.barcode.OffProduct
import com.battlesbudz.dinnerbot.feature.barcode.OpenFoodFactsLookup
import kotlinx.coroutines.launch

/**
 * The one add-an-ingredient form (v0.6.2 hotfix), shared by every door
 * that puts a new item in the price book: the ingredient editor in
 * recipe creation, editing, the cook view, and import review; the
 * price book's own add button; and a barcode scan of a product the
 * book does not have. Every field and every outcome is the same
 * wherever the user meets it.
 *
 * The price is OPTIONAL — that is the fix. Leaving it blank creates
 * the item anyway at a guessed price marked as a guess
 * ([NewBookItem] runs the guess machinery), so an ingredient she
 * cannot price on the spot can never dead-end the flow she is in.
 * Typing a price makes it her number, user-corrected, no guess mark.
 *
 * [pricedSource]/[guessSource] name the door on the saved item, the
 * way scans have always recorded "Added by barcode scan".
 *
 * Scanning (v0.6.3): the form carries its own "Scan barcode" button,
 * so building the book no longer means detouring through the shopping
 * list. A scan looks the code up in Open Food Facts and PREFILLS what
 * it finds — product name, pack size parsed from the OFF quantity
 * data, the serving folded into the pack description — as suggestions
 * she can edit, exactly like the name hint before it; an unparseable
 * pack or a missing product simply leaves the fields to her and never
 * blocks. The scanned barcode stays linked to the item she saves
 * ([onBarcodeLinked]), and the label's nutrition facts are captured
 * onto the item ([onLabelFacts]) so recipes costed from it read the
 * label instead of the USDA generic. When the scanned code already
 * belongs to a book item ([existingItemForBarcode] finds it), the
 * form hands that item back through [onExistingItemScanned] instead
 * of creating a duplicate, and its stored facts refresh from the new
 * scan. [initialBarcode] runs the same lookup for a form that opens
 * with the barcode already known (the shopping list's new-item form),
 * which is also why that door hides the scan button.
 */
@Composable
fun AddPriceItemDialog(
    book: List<PriceItem>,
    today: String,
    onConfirm: (PriceItem) -> Unit,
    onDismiss: () -> Unit,
    initialName: String = "",
    /** Store id the item starts at; blank resolves to the user's first store. */
    initialStore: String = "",
    /** The user's stores, offered in the store picker (Phase C1). */
    stores: List<Store> = emptyList(),
    title: String = "Add an ingredient",
    intro: String = "Anything not in the price book yet can start here. " +
        "The price can be left blank — Dinner Bot will guess it and mark it as a guess " +
        "until the item is shopped.",
    confirmLabel: String = "Add to the price book",
    pricedSource: String = NewBookItem.HAND_PRICED_SOURCE,
    guessSource: String = NewBookItem.HAND_GUESS_SOURCE,
    /** Show the Scan barcode button; the shopping list's form opens with the code already scanned. */
    showScanButton: Boolean = true,
    /** A barcode known before the form opened: its lookup (facts + pack prefill) runs on open. */
    initialBarcode: String? = null,
    /** The lookup a door already ran for [initialBarcode] (the link picker): applied directly, never refetched. */
    initialProduct: OffProduct? = null,
    /** Resolves a scanned barcode to the book item it already belongs to, when the door knows the links. */
    existingItemForBarcode: ((String) -> PriceItem?)? = null,
    /** A scan matched this existing item: the door uses it instead of a new item, and the form closes. */
    onExistingItemScanned: (PriceItem) -> Unit = {},
    /** The barcode to link to the item this form saves. */
    onBarcodeLinked: (barcode: String, itemId: String) -> Unit = { _, _ -> },
    /** Label nutrition facts captured for an item id, from a scan in (or before) this form. */
    onLabelFacts: (itemId: String, facts: LabelNutrition) -> Unit = { _, _ -> },
    /** The Open Food Facts lookup, injectable so tests can fixture it. */
    productLookup: suspend (String) -> OffProduct? = { OpenFoodFactsLookup.lookupProduct(it) },
    /** The camera surface, injectable so tests can fixture a scan without a camera. */
    scannerContent: @Composable (onScanned: (String) -> Unit, onDismissScanner: () -> Unit) -> Unit =
        { onScanned, onDismissScanner -> BarcodeScannerDialog(onScanned, onDismissScanner) },
) {
    // The premium scan allowance (Phase C3): the app's gate is asked
    // before this form's camera opens and told about each barcode the
    // camera decodes. A code handed in through [initialBarcode] was
    // already counted where it was scanned, so it never passes here.
    // Null (tests, previews) means ungated, as before this phase.
    val scanGate = LocalScanGate.current
    val categories = remember(book) {
        (book.map { it.category }.distinct().sorted() + "Other").distinct()
    }
    var name by remember { mutableStateOf(initialName) }
    val storeOptions = remember(stores) { stores.ifEmpty { PresetStores.ALL } }
    var store by remember {
        mutableStateOf(
            initialStore.ifBlank {
                stores.firstOrNull()?.id ?: book.firstOrNull()?.homeStore ?: storeOptions.first().id
            },
        )
    }
    var pickedCategory by remember { mutableStateOf<String?>(null) }
    // The category the form will save: the user's own pick once they
    // make one. Before that, the category of a book item this name
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

    // Scan state (v0.6.3): the barcode this form is bound to once one
    // is scanned (or was handed in), the product the lookup returned,
    // and whether the camera is open.
    val scope = rememberCoroutineScope()
    var scannedBarcode by remember { mutableStateOf(initialBarcode) }
    var scannedProduct by remember { mutableStateOf<OffProduct?>(null) }
    // True while a known-barcode hand-off lookup is in flight: the form
    // locks (no save, no new scan) so a fast Save cannot create the very
    // duplicate the hand-off exists to prevent (Codex review, v0.6.3).
    var handingOff by remember { mutableStateOf(false) }
    var scanning by remember { mutableStateOf(false) }
    var scanNote by remember { mutableStateOf<String?>(null) }
    var cameraDenied by remember { mutableStateOf(false) }

    fun applyPrefill(product: OffProduct) {
        val productName = product.name
        if (name.isBlank() && !productName.isNullOrBlank()) {
            name = productName
        }
        val pack = OffPackParser.parse(
            quantityText = product.quantityText,
            productQuantity = product.productQuantity,
            productQuantityUnit = product.productQuantityUnit,
            servingSizeText = product.servingSizeText,
        )
        val packDescriptionSuggestion = pack.description
        if (packDescription.isBlank() && !packDescriptionSuggestion.isNullOrBlank()) {
            packDescription = packDescriptionSuggestion
        }
        val packAmountSuggestion = pack.amount
        val packUnitSuggestion = pack.unit
        if (packAmountText.isBlank() && packAmountSuggestion != null && packUnitSuggestion != null) {
            packAmountText = formatPackAmount(packAmountSuggestion)
            unit = packUnitSuggestion
        }
        scanNote = "Filled in from the barcode lookup — check it over; " +
            "everything here can be edited."
    }

    fun handleBarcode(code: String) {
        scanning = false
        val existing = existingItemForBarcode?.invoke(code)
        if (existing != null) {
            // Already in the book: refresh its label facts from this
            // scan, hand the item back, and close — no duplicate. The
            // refresh runs in this form's scope and closing the form
            // cancels that scope, so the callbacks and the close wait
            // until the lookup has landed (Codex review, v0.6.3).
            scanNote = "That barcode is already in the book — " +
                "refreshing its label facts…"
            handingOff = true
            scope.launch {
                val product = runCatching { productLookup(code) }.getOrNull()
                product?.labelNutrition(existing.id, code, System.currentTimeMillis())
                    ?.let { facts -> onLabelFacts(existing.id, facts) }
                onExistingItemScanned(existing)
                onDismiss()
            }
            return
        }
        scannedBarcode = code
        scannedProduct = null
        scope.launch {
            val product = runCatching { productLookup(code) }.getOrNull()
            // A newer scan supersedes this one: the shared state has
            // moved on, so a late result must not prefill the form or
            // attach its facts to the newer barcode (Codex review).
            if (scannedBarcode != code) return@launch
            if (product == null) {
                scanNote = "That barcode is not in Open Food Facts. Fill the form " +
                    "in from the package — the barcode still links to the item you save."
            } else {
                scannedProduct = product
                applyPrefill(product)
            }
        }
    }

    LaunchedEffect(initialBarcode) {
        val code = initialBarcode ?: return@LaunchedEffect
        if (initialProduct != null && existingItemForBarcode?.invoke(code) == null) {
            // The door already paid for this lookup: apply it as-is.
            scannedBarcode = code
            scannedProduct = initialProduct
            applyPrefill(initialProduct)
        } else {
            handleBarcode(code)
        }
    }

    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        if (granted) {
            scanning = true
        } else {
            cameraDenied = true
        }
    }
    fun startScan() {
        if (handingOff) return
        // The gate decides before the camera opens (Phase C3); when
        // it says no, the form simply stays a form, filled in by hand.
        if (scanGate != null && !scanGate.canStartScan()) return
        cameraDenied = false
        if (cameraGranted) {
            scanning = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val packAmount = packAmountText.toDoubleOrNull()
    val priceCents = if (priceText.isBlank()) null else PriceCorrection.parseToCents(priceText)
    val canSave = !handingOff && name.isNotBlank() &&
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
                if (showScanButton) {
                    Button(
                        onClick = { startScan() },
                        modifier = Modifier.fillMaxWidth().testTag("add-item-scan"),
                    ) { Text("Scan barcode") }
                }
                if (cameraDenied) {
                    Text(
                        "Camera permission is off, so the barcode cannot be scanned. " +
                            "The form still works — type the ingredient in by hand.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("add-item-camera-denied"),
                    )
                }
                scanNote?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.testTag("add-item-scan-note"),
                    )
                }
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
                    ) { Text("Store: ${storeDisplayName(store, stores)}") }
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
                    val item = NewBookItem.build(
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
                    )
                    val barcode = scannedBarcode
                    if (barcode != null) {
                        scannedProduct
                            ?.labelNutrition(item.id, barcode, System.currentTimeMillis())
                            ?.let { facts -> onLabelFacts(item.id, facts) }
                        onBarcodeLinked(barcode, item.id)
                    }
                    onConfirm(item)
                },
                modifier = Modifier.testTag("add-item-confirm"),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (scanning) {
        scannerContent(
            { code ->
                // One decoded barcode is one scan against the
                // allowance (Phase C3); an abandoned camera is none.
                scanGate?.onScanCompleted()
                handleBarcode(code)
            },
            { scanning = false },
        )
    }

    when (picker) {
        "store" -> AddItemOptionPicker(
            title = "Home store",
            options = storeOptions.map { it.name to it.id },
            onPick = { picked ->
                store = picked
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

/** A scanned pack amount as the form's text: whole amounts without a decimal tail. */
private fun formatPackAmount(amount: Double): String =
    if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        amount.toString()
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
